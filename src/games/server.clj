(ns games.server
  (:require
   [games.ring :as ring]
   [ring.adapter.jetty :as jetty]))

(defn -main
  [& args]
  (jetty/run-jetty #'ring/app {:port 8000 :join? false}))
