(ns drawknife.middleware
  (:require
    [drawknife.core :as log]
    [nano-id.core :refer [nano-id]]))


(defn wrap-logging-atom-ctx
  "Update logger with more context from the request, where 'ctx-keys' are keys in the request,
  whose values should be included in the logger."
  [handler log-key ctx-fn]
  (fn [req]
    (let [logger          (get req log-key)
          logger-with-ctx (log/with @logger merge (ctx-fn req))]
      (reset! logger logger-with-ctx)
      (handler req))))


(defn wrap-logger-atom-deref
  "Middleware to deref the logger before request is handed off to routes."
  [handler log-key]
  (fn [req]
    (handler (update req log-key deref))))


(defn wrap-request-logger-atom
  "Wrap logger context atom for requests when context need to be kept between middlewares,
  use wrap-logger-atom-deref to deref the atom"
  [handler log-key & [{:keys [ctx-fn req-keys init]
                       :or   {ctx-fn (constantly nil)}}]]
  (fn [req]
    (let [ctx    (merge (select-keys req (into [:request-method :uri :url :remote-addr] req-keys))
                        (ctx-fn req)
                        {:request-id (nano-id)})
          logger (log/with (or init (log/timbre-logger :info :println nil))
                           merge ctx)]
      (handler (assoc req log-key (atom logger))))))