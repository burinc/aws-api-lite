;; Copyright (c) Cognitect, Inc.
;; All rights reserved.

(ns ^:skip-wiki cognitect.aws.endpoint
  "Impl, don't call directly."
  (:refer-clojure :exclude [resolve])
  (:require [clojure.string :as str]
            [cognitect.aws.util :as util]))

(def endpoints (atom nil))

(defn set-endpoints [data] (reset! endpoints data))

(defn render-template
  [template args]
  (str/replace template
               #"\{([^}]+)\}"
               #(get args (second %))))

(defn service-resolve
  "Resolve the endpoint for the given service."
  [partition service-name service region-key]
  (let [endpoint (get-in service [:endpoints region-key])
        region   (name region-key)
        result   (merge (:defaults partition)
                        (:defaults service)
                        endpoint
                        {:partition (:partition partition)
                         :region    region
                         :dnsSuffix (:dnsSuffix partition)})]
    (util/map-vals #(render-template % {"service"   service-name
                                        "region"    region
                                        "dnsSuffix" (:dnsSuffix partition)})
                   result
                   [:hostname :sslCommonName])))

(defn partition-resolve
  [{:keys [services] :as partition} service-key region-key]
  (when (contains? (-> partition :regions keys set) region-key)
    (let [{:keys [partitionEndpoint isRegionalized] :as service} (get services service-key)
          endpoint-key (if (and partitionEndpoint (not isRegionalized))
                         (keyword partitionEndpoint)
                         region-key)]
      (service-resolve partition (name service-key) service endpoint-key))))

(defn resolve*
  "Resolves an endpoint for a given service and region.

  service keyword Identify a AWS service (e.g. :s3)
  region keyword  Identify a AWS region (e.g. :us-east-1).

  Return a map with the following keys:

  :partition            The name of the partition.
  :region               The region of the endpoint.
  :hostname             The hostname to use.
  :ssl-common-name      The ssl-common-name to use (optional).
  :credential-scope     The Signature v4 credential scope (optional).
  :signature-versions   A list of possible signature versions (optional).
  :protocols            A list of supported protocols."
  [service-key region]
  (if-let [{:keys [partitions]} @endpoints]
    (some #(partition-resolve % service-key region) partitions)
    (throw (ex-info "Endpoints are not defined" {:service-key service-key
                                                 :region region}))))

(def resolve (memoize resolve*))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defprotocol EndpointProvider
  (-fetch [provider region]))

(defn default-endpoint-provider [api endpointPrefix endpoint-override]
  (reify EndpointProvider
    (-fetch [_ region]
      (if-let [ep (resolve (keyword endpointPrefix) (keyword region))]
        (merge ep (if (string? endpoint-override)
                    {:hostname endpoint-override}
                    endpoint-override))
        {:cognitect.anomalies/category :cognitect.anomalies/fault
         :cognitect.anomalies/message "No known endpoint."}))))

(defn fetch [provider region]
  (-fetch provider region))
