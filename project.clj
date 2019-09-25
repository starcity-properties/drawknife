(defproject starcity/drawknife "1.1.2"
  :description "Logging configuration & middleware for Starcity projects."
  :url "https://github.com/starcity-properties/drawknife"
  :license {:name "Eclipse Public License"
            :url  "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.9.0"]
                 [cheshire "5.7.1"]
                 [com.taoensso/timbre "4.10.0"]
                 [nano-id "0.9.3"]]

  :plugins [[s3-wagon-private "1.2.0"]]

  :repositories {"releases"  {:url        "s3://starjars/releases"
                              :username   :env/aws_access_key
                              :passphrase :env/aws_secret_key}
                 "snapshots" {:url        "s3://starjars/snapshots"
                              :username   :env/aws_access_key
                              :passphrase :env/aws_secret_key}})
