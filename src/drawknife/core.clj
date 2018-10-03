(ns drawknife.core
  (:require [cheshire.core :as json]
            [clojure.spec.alpha :as s]
            [taoensso.timbre :as timbre]
            [taoensso.timbre.appenders.3rd-party.rolling :as rolling]
            [taoensso.timbre.appenders.core :as appenders]
            [clojure.string :as string])
  (:import (java.io StringWriter PrintWriter LineNumberReader StringReader)
           (clojure.lang Atom)))




;; =============================================================================
;; Exceptions
;; =============================================================================


(defn- clean-ex-data
  "Returns only first level atomic values from the ex-data map."
  [data]
  (when (map? data)
    (into {}
          (filter (fn [[_ v]]
                    (or (string? v)
                        (number? v)
                        (keyword? v)
                        (boolean? v)
                        (symbol? v)
                        (nil? v))))
          data)))

;; Inspired by:
;; https://github.com/kikonen/log4j-share/blob/master/src/main/java/org/apache/log4j/DefaultThrowableRenderer.java#L56
;(defn- stacktrace-seq [^Throwable x]
;  (let [sw (StringWriter.)
;        pw (PrintWriter. sw)]
;    (try
;      (.printStackTrace x pw)
;      (catch Exception _))
;    (.flush pw)
;
;    (let [reader (LineNumberReader. (StringReader. (.toString sw)))]
;      (->> (repeatedly #(.readLine reader))
;           (take-while some?)))))

(defn render-throwable [^Throwable x]
  (cond-> {:exception-message (.getMessage x)
           :exception-class   (.getCanonicalName (.getClass x))
           #_#_:stacktrace (string/join "\newline" (stacktrace-seq x))}
          (some? (ex-data x))
          (assoc :exception-data (clean-ex-data (ex-data x)))))


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


(defprotocol ILogger
  (-log [this msg]))


(defn timbre-logger [log-level appender filename]
  (timbre/merge-config!
    (configuration log-level appender filename))
  (reify ILogger
    (-log [_ {:keys [level id millis throwable data]}]
      (if (some? throwable)
        (timbre/log level throwable id (assoc data :time millis))
        (timbre/log level id (assoc data :time millis))))))


(defn with [logger f]
  (reify ILogger
    (-log [_ msg]
      (let [m (f (:data msg))]
        (-log logger (assoc msg :data m))))))


;; =============================================================================
;; Helpers
;; =============================================================================


(defn- log! [level logger id data & {:keys [throwable]}]
  (let [logger (if (instance? Atom logger) @logger logger)]
    (-log logger {:id        id
                  :level     level
                  :data      data
                  :throwable throwable
                  :millis    (System/currentTimeMillis)})))


;; =============================================================================
;; Logging
;; =============================================================================


(defn debug [logger id data]
  (log! :debug logger id data))


(defn info [logger id data]
  (log! :info logger id data))


(defn warn [logger id data]
  (log! :warn logger id data))


(defn error [logger id data & [throwable]]
  (log! :error logger id data :throwable throwable))
