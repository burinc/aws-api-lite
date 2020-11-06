;; Copyright (c) Cognitect, Inc.
;; All rights reserved.

(ns ^:skip-wiki cognitect.aws.protocols.ec2
  "Impl, don't call directly."
  (:require [cognitect.aws.util :as util]
            [cognitect.aws.shape :as shape]
            [cognitect.aws.protocols.query :as query]))

(defn serialized-name
  [shape default]
  (or (:queryName shape)
      (when-let [name (:locationName shape)]
        (apply str (Character/toUpperCase (first name)) (rest name)))
      default))

(defmulti serialize
  (fn [shape args serialized prefix] (:type shape)))

(defmethod serialize :default
  [shape args serialized prefix]
  (query/serialize shape args serialized prefix))

(defmethod serialize "structure"
  [shape args serialized prefix]
  (let [args (util/with-defaults shape args)]
    (reduce (fn [serialized k]
              (let [member-shape (shape/member-shape shape k)
                    member-name  (serialized-name member-shape (name k))]
                (if (contains? args k)
                  (serialize member-shape (k args) serialized (conj prefix member-name))
                  serialized)))
            serialized
            (keys (:members shape)))))

(defmethod serialize "list"
  [shape args serialized prefix]
  (let [member-shape (shape/list-member-shape shape)]
    (reduce (fn [serialized [i member]]
              (serialize member-shape member serialized (conj prefix (str i))))
            serialized
            (map-indexed (fn [i member] [(inc i) member]) args))))

(defn build [service op-map]
  (query/build-query-http-request serialize service op-map))

(defn parse [service op-map http-response]
  (query/build-query-http-response service op-map http-response))
