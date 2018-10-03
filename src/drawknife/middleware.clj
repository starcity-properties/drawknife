(ns drawknife.middleware
  (:require
    [drawknife.core :as log]))


(defn wrap-logging-ctx
  "Update logger with more context from the request, where 'ctx-keys' are keys in the request,
  whose values should be includede in the logger."
  [handler ctx]
  (fn [{:keys [deps] :as req}]
    (let [logger-with-ctx (log/with @(:logger deps) #(merge % (ctx req)))]
      (reset! (:logger deps) logger-with-ctx)
      (handler req))))


(defn wrap-logger-deref
  "Middleware to deref the logger before request is handed off to routes."
  [handler]
  (fn [req]
    (handler (update-in req [:deps :logger] deref))))


(defn wrap-logger-atom
  "Update logger to be an atom.

  Note: This is needed while the requests is going through the middlewares to have the context
  be included if exceptions would happen. Make sure to use wrap-logger-deref before request
  is leaving the middlewares and handed off to routes.  "
  [handler]
  (fn [{:keys [deps] :as req}]
    (let [logger (:logger deps (log/timbre-logger :info :println nil))]
      (handler (assoc-in req [:deps :logger] (atom logger))))))


(defn something []
  (log/info {} ::id {:a 1} (ex-info "test" {})))