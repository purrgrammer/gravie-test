(ns ^:figwheel-hooks games.core
  (:require
   [goog.dom :as gdom]
   [clojure.string :as string]
   [clojure.walk :refer [keywordize-keys]]
   [goog.net.XhrIo :as xhr]
   [rum.core :as rum]))

(defn remove-from-cart
  [state game]
  (-> state
      (update :cart disj game)
      (update :quantities dissoc (:id game))))

(def api-timeout 10000)

(defn search!
  [query resolve reject]
  (let [api-url (str "/search/" query)
        headers (clj->js {"Accept" "application/json"
                          "Content-Type" "application/json"})
        cb (fn [event]
             (let [response (-> event .-target)]
               (case (.getStatus response)
                 200
                 (let [resp (.getResponseJson response)]
                   (resolve (keywordize-keys (js->clj resp))))
                 (reject :network-error))))]
    (xhr/send api-url cb "GET" "" headers api-timeout)))

(defonce app-state
  (atom {:page :search
         :search nil
         :cart #{}}))

(defn get-app-element []
  (gdom/getElement "app"))

(rum/defc search-form
  [state]
  (let [[error error!] (rum/use-state nil)
        [loading? loading!] (rum/use-state false)
        disabled? (if loading?
                    "disabled"
                    "")
        search-ref (rum/use-ref nil)]
    [:section.search
     [:h2 "Search"]
     [:form.search-form
      [:input.search-field
       {:type "text"
        :disabled disabled?
        :on-change (fn [ev]
                     (error! nil))
        :placeholder "Search for a game"
        :ref search-ref}]
      [:button.search-button
       {:disabled disabled?
        :on-click (fn [ev]
                    (.preventDefault ev)
                    (let [ref (rum/deref search-ref)
                          query (.-value ref)]
                      (when-not (string/blank? query)
                        (loading! true)
                        (search! query
                                 (fn [result]
                                   (loading! false)
                                   (set! (.-value ref) "")
                                   (swap! state assoc :search {:query query
                                                               :result result}))
                                 (fn [err]
                                   (loading! false)
                                   (error! (str "Error when searching \"" query "\".")))))))}
       "Search"]]
     (cond
       error
       [:span.info.error error]

       loading?
       [:span.info.msg
        "Searching.."])]))

(rum/defc search-results < rum/reactive
  [state]
  (let [{:keys [search cart]} (rum/react state)]
    (if (nil? search)
      [:section.no-results
       [:p "No results yet. Go ahead and search for some games!"]]
      (let [{:keys [query result]} search]
        (if (empty? result)
          [:section.empty-result
           [:p "Nothing found for " "\"" query "\". Try another query."]]
          [:section.search-results
           [:h3
            "Results for " "\"" query "\""]
           [:ul.games
            (for [{:keys [id
                          name
                          deck
                          image
                          url
                          platforms
                          rating]
                   :as game} result
                  :let [in-cart? (contains? cart game)
                        image-url (:tiny_url image)]]
              [:li.game
               {:key (str id)}
               [:h4 [:a {:href url} name]]
               (if-not (string/includes? image-url "gb_default")
                 [:img.game-image
                  {:src (:small_url image)
                   :alt name}]
                 [:p.no-image "No image available"])
               (when platforms
                 [:div
                  [:h5 "Platforms"]
                  [:ul.platforms
                   (for [{:keys [name
                                 abbreviation
                                 site_detail_url]} platforms]
                     [:li.platform
                      {:key name}
                      [:a {:href site_detail_url}
                       name]])]])
               (when rating
                 [:div
                  [:h5 "Ratings"]
                  [:ul.rating
                   (for [{:keys [name]} rating]
                     [:li.rating
                      {:key name}
                      name])]])
               [:p.deck deck]
               (if in-cart?
                 [:button.remove-from-cart
                  {:on-click #(swap! state remove-from-cart game)}
                  "Remove"]
                 [:button.add-to-cart
                  {:on-click #(swap! state update :cart conj game)}
                  "Add"])])]])))))

(rum/defc cart-nav < rum/reactive
  [state]
  (let [{:keys [page
                cart]} (rum/react state)
        checkout [:button
                  {:on-click #(swap! state assoc :page :checkout)}
                  "Checkout"]
        search [:button
                {:on-click #(swap! state assoc :page :search)}
                "Search"]
        button (if (= page :search) checkout search)
        items (count cart)]
    (cond
        (= 0 items)
        [:section.cart-nav
         "Empty"
         (when (= page :checkout)
           search)]

        (= 1 items)
        [:section.cart-nav
         "1 item"
         button]

        :else
        [:section.cart-nav
         items " items"
         button])))

(rum/defc checkout < rum/reactive
  [state]
  (let [{:keys [cart
                quantities]} (rum/react state)]
    [:section.checkout-page
     [:h2 "Checkout"]
     [:ol.checkout
      (for [{:keys [id name url image] :as game} (sort-by :name cart)
            :let [image-url (:small_url image)]]
        [:li.checkout-item
         {:key (str id)}
         [:section.thirty
          (if-not (string/includes? image-url "gb_default")
            [:img
             {:src image-url
              :width "50%"
              :height "auto"
              :alt name}]
            [:p.no-image "No image available"])]
         [:section.name.fifty
          [:a
           {:href url}
           name]]
         [:section.item-controls.twenty
          [:input.quantity
           {:type "number"
            :min "1"
            :value (get quantities id 1)
            :on-change (fn [ev]
                         (swap! state
                                assoc-in
                                [:quantities id]
                                (.-value (.-target ev))))}]
          [:button.remove-from-cart
           {:on-click #(swap! state remove-from-cart game)}
           "Remove"]]])]]))

(rum/defc app < rum/reactive
  [state]
  (let [{:keys [page cart] :as st} (rum/react state)]
    (case page
      :search [:section.main
               (cart-nav state)
               (search-form state)
               (search-results state)]
      :checkout [:section.main
                 (cart-nav state)
                 (checkout state)])))

(defn mount [el state]
  (rum/mount (app state) el))

(defn mount-app-element []
  (when-let [el (get-app-element)]
    (mount el app-state)))

;; conditionally start your application based on the presence of an "app" element
;; this is particularly helpful for testing this ns without launching the app
(mount-app-element)

;; specify reload hook with ^;after-load metadata
(defn ^:after-load on-reload []
  (mount-app-element)
  ;; optionally touch your app-state to force rerendering depending on
  ;; your application
  ;; (swap! app-state update-in [:__figwheel_counter] inc)
)
