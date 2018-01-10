(defproject starcity/drawknife "1.0.0-SNAPSHOT"
  :description "Logging configuration & middleware for Starcity projects."
  :url "https://github.com/starcity-properties/drawknife"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.9.0"]
                 [cheshire "5.7.1"]
                 [com.taoensso/timbre "4.10.0"]]

  :plugins [[s3-wagon-private "1.2.0"]]

  :repositories {"releases" {:url        "s3://starjars/releases"
                             :username   :env/aws_access_key
                             :passphrase :env/aws_secret_key}})
