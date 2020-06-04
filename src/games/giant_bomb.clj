(ns games.giant-bomb
  (:require
   [clj-http.client :as client]
   [clojure.java.io :as io]
   [jsonista.core :as json]
   [hickory.core :as html]))

(def config
  (read-string (slurp (io/resource "config.edn"))))

(defn search-request
  [api-key query page]
  {:method :get
   :url (str "https://www.giantbomb.com/api/search/?"
             "api_key=" api-key "&"
             "limit=10" "&"
             "field_list=id,name,deck,image,site_detail_url,original_game_rating,platforms,"
             "resources=game" "&"
             "page=" page "&"
             "query=" query "&"
             "format=json")
   :headers {"User-Agent" "Agent Smith"}})

(defn search-page!
  [query page acc]
  (let [req (search-request (:api-key config)
                            query
                            page)]
    (let [resp (client/request req)]
      (if (= 200 (:status resp))
        (let [{:strs [results]} (json/read-value (:body resp))]
          (if (or (empty? results)
                  (>= page (:max-pages config)))
            acc
            (recur query
                   (inc page)
                   (into acc results))))
        acc))))

(defn parse-result
  [{:strs [id
           name
           deck
           image
           site_detail_url
           original_game_rating
           platforms] :as raw}]
  {:id id
   :name name
   :deck deck
   :url site_detail_url
   :rating original_game_rating
   :image image
   :platforms platforms})

(defn game?
  [{:strs [resource_type]}]
  (= "game" resource_type))

(defn search!
  [query]
  (let [results (search-page! query 1 [])
        xform (comp
               (filter game?)
               (map parse-result))]
    (transduce xform conj results)))
