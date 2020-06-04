(ns games.ring
  (:require
   [games.giant-bomb :as api]
   [reitit.core :as r]
   [reitit.ring :as ring]
   [reitit.ring.middleware.exception :as exception]
   [reitit.ring.middleware.muuntaja :as muuntaja]
   [muuntaja.core :as m]))

(defn search-handler
  [{:keys [path-params]}]
  (let [results (api/search! (:query path-params))]
    {:status 200
     :body results}))

(def router
  (ring/router
   ["/search/:query" {:get search-handler}]
   {:data {:muuntaja m/instance
           :middleware [exception/exception-middleware
                        muuntaja/format-middleware
                        muuntaja/format-negotiate-middleware
                        muuntaja/format-response-middleware]}}))

(def static
  (ring/create-resource-handler {:path "/"}))

(def app
  (ring/ring-handler router
                     (ring/routes
                      static
                      (ring/create-default-handler))))
