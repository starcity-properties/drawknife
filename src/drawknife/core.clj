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
  {:level      log-level
   :middleware [wrap-event-format]
   :appenders  (appender-config appender {:filename filename})})


;; =============================================================================
;; Logger machinery
;; =============================================================================


(defn timbre-logger [log-level appender filename]
  (timbre/merge-config!
    (configuration log-level appender filename))
  {})


(defn with [logger f]
  (update logger :data f))


;; =============================================================================
;; Helpers
;; =============================================================================


(defmacro log! [level logger id data & [{:keys [throwable]}]]
  (let [millis   (System/currentTimeMillis)
        log-data (merge logger (assoc data :millis millis))]
    (if (some? throwable)
      `(timbre/log ~level ~throwable ~id ~log-data)
      `(timbre/log ~level ~id ~log-data))))


;; =============================================================================
;; Logging
;; =============================================================================


(defmacro debug [logger id data & [throwable]]
  `(log! :debug ~logger ~id ~data ~{:throwable throwable}))


(defmacro info [logger id data & [throwable]]
  `(log! :info ~logger ~id ~data ~{:throwable throwable}))


(defmacro warn [logger id data & [throwable]]
  `(log! :warn ~logger ~id ~data ~{:throwable throwable}))


(defmacro error [logger id data & [throwable]]
  `(log! :error ~logger ~id ~data ~{:throwable throwable}))
