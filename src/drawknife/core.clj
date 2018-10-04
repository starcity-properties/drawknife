(ns drawknife.core
  (:require [cheshire.core :as json]
            [clojure.spec.alpha :as s]
            [taoensso.timbre :as timbre]
            [taoensso.timbre.appenders.3rd-party.rolling :as rolling]
            [taoensso.timbre.appenders.core :as appenders]))


;; =============================================================================
;; Configurations
;; =============================================================================


(defn- appender-config [appender & [{:keys [filename]}]]
  (case appender
    :spit {:spit (appenders/spit-appender {:fname filename})}
    :rolling {:rolling (rolling/rolling-appender {:path filename})}
    {:println (appenders/println-appender {:stream :auto})}))


(s/def ::event-vargs
  (s/cat :event keyword?
         :params map?))


(defn- event-vargs
  [data event params]
  (try
    (assoc data :vargs
                [(-> {:event event}
                     (merge (when-let [err (:?err data)]
                              {:error-data (or (ex-data err) :none)})
                            params)
                     json/generate-string)])
    (catch Throwable t
      (timbre/warn t "Error encountered while attempting to encode vargs.")
      data)))


(defn- wrap-event-format
  "Middleware that transforms the user's log input into a JSON
  string with an `event` key. This is used to make search effective in LogDNA.

  Only applies when timbre is called with input of the form:

  (timbre/info ::event {:map :of-data})"
  [{:keys [vargs] :as data}]
  (if (s/valid? ::event-vargs vargs)
    (let [{:keys [event params]} (s/conform ::event-vargs vargs)]
      (event-vargs data event params))
    data))


(defn- configuration
  "The timbre configuration."
  [log-level appender & [filename]]
  (merge
    (update timbre/example-config :appenders dissoc :println)
    {:level      log-level
     :middleware [wrap-event-format]
     :appenders  (appender-config appender {:filename filename})}))


;; =============================================================================
;; Logger machinery
;; =============================================================================


(defn timbre-logger
  "Setup timbre logging with provided configuration."
  [log-level appender filename]
  {:config (configuration log-level appender filename)})


(defn with
  "Add more context to an existing log context 'logger' by calling 'f' on the
   content of the given log context."
  [logger f & args]
  (apply update logger :data f args))


;; =============================================================================
;; Helpers
;; =============================================================================


(defmacro log!
  "Log 'data' via timbre on a specific 'level' and with context 'logger', where 'id'
  should be a namespace'd keyword and 'data' is the information to log. If 'log-ctx'
  is nil, 'data' will be the only output. Potential exception is expected as the
  first element of 'args'."
  [level logger id data & args]
  (println logger)
  `(let [level#     ~level
         id#        ~id
         log-ctx#   ~logger
         throwable# ~(first args)
         config#    (:config log-ctx#)
         log-data#  (merge (:data log-ctx#)
                           (assoc ~data :millis (System/currentTimeMillis)))]
     (timbre/with-config
       config#
       (if (some? throwable#)
         (timbre/log level# throwable# id# log-data#)
         (timbre/log level# id# log-data#)))))


;; =============================================================================
;; Logging
;; =============================================================================


(defmacro debug [log-ctx id data & args] `(log! :debug ~log-ctx ~id ~data ~@args))
(defmacro info [log-ctx id data & args] `(log! :info ~log-ctx ~id ~data ~@args))
(defmacro warn [log-ctx id data & args] `(log! :warn ~log-ctx ~id ~data ~@args))
(defmacro error [log-ctx id data & args] `(log! :error ~log-ctx ~id ~data ~@args))
