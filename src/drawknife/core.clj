(ns drawknife.core
  (:require [cheshire.core :as json]
            [clojure.spec.alpha :as s]
            [clojure.string :as string]
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


(defn- kebab->snake
  "To snake case, excluding the : character from a keyword."
  [s]
  (-> (string/replace (str s) #":(.)" "$1")
    (string/replace #"[-/.]" "_")))


(defn- event-vargs
  [data event params]
  (try
    (assoc data :vargs
                [(-> {:event event}
                   (merge (when-let [err (:?err data)]
                            {:error-data (or (ex-data err) :none)})
                     params)
                   (json/generate-string {:key-fn kebab->snake}))])
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
  [log-level & [appender filename]]
  (let [custom {:level          log-level
                :timestamp-opts {:pattern  "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"
                                 :locale   :jvm-default
                                 :timezone :utc}
                :middleware     [wrap-event-format]}]
    (cond->
      (merge (update timbre/example-config :appenders dissoc :println)
        custom)

      (some? appender)
      (assoc :appenders (appender-config appender {:filename filename})))))


;; =============================================================================
;; Logger machinery
;; =============================================================================


(defn with
  "Add more context to an existing log context 'logger' by calling 'f' on the
   content of the given log context."
  [logger f & args]
  (apply update logger :data f args))


;; =============================================================================
;; Helpers
;; =============================================================================


(defn output-json
  ([] (output-json nil))
  ([opts]
   (fn [data]
     (let [{:keys [no-stacktrace? stacktrace-fonts]} opts
           {:keys [level ?err #_vargs msg_ ?ns-str ?file hostname_
                   timestamp_ ?line]} data]
       (json/generate-string
         (cond->
           {:timestamp (force timestamp_)
            :host      (force hostname_)
            :level     (clojure.string/upper-case (name level))
            :ns        (or ?ns-str ?file "?")
            :line      (or ?line "?")
            :msg       (json/parse-string (force msg_))}

           (and (not no-stacktrace?) (some? ?err))
           (merge (timbre/stacktrace ?err))))))))

(defmacro log!
  "Log 'data' via timbre on a specific 'level' and with context 'logger', where 'id'
  should be a namespace'd keyword and 'data' is the information to log. If 'log-ctx'
  is nil, 'data' will be the only output. Potential exception is expected as the
  first element of 'args'."
  [level logger id data & args]
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


;; =============================================================================
;; Loggers
;; =============================================================================

(defn timbre-logger
  "Setup timbre logging with provided configuration."
  [log-level appender filename]
  {:config (configuration log-level appender filename)})


(defn timbre-json-println-logger
  [log-level & [{:keys [appenders filename]}]]
  (let [app-configs (reduce (fn [m k]
                              (merge m (appender-config k {:filename filename})))
                      {}
                      appenders)
        config      (-> (configuration log-level)
                      (assoc :output-fn (output-json))
                      (update :appenders merge app-configs))]
    {:config config}))
