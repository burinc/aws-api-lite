;; Copyright (c) Cognitect, Inc.
;; All rights reserved.

(ns cognitect.aws.test.ec2-metadata-utils-server
  "Modeled after com.amazonaws.util.EC2MetadataUtilsServer"
  (:require [cognitect.aws.ec2-metadata-utils :as ec2-metadata-utils]
            [cognitect.aws.util :as u]
            [org.httpkit.server :as http-server]
            [cheshire.core :as json]))

(def iam-info
  (json/generate-string
    {"Code" "Success"
     "LastUpdated" "2014-04-07T08 18 41Z"
     "InstanceProfileArn" "foobar"
     "InstanceProfileId" "moobily"
     "NewFeature" 12345}))

(def iam-cred-list
  "test1\ntest2")

(def iam-cred
  (json/generate-string
    {"Code"  "Success"
     "LastUpdated" "2014-04-07T08:18:41Z"
     "Type" "AWS-HMAC"
     "AccessKeyId" "foobar"
     "SecretAccessKey" "it^s4$3cret!"
     "Token" "norealvalue"
     "Expiration" "2014-04-08T23:16:53Z"}))

(def instance-info
  (json/generate-string
    {"pendingTime" "2014-08-07T22:07:46Z"
     "instanceType" "m1.small"
     "imageId" "ami-a49665cc"
     "instanceId" "i-6b2de041"
     "billingProducts" ["foo"]
     "architecture" "x86_64"
     "accountId" "599169622985"
     "kernelId" "aki-919dcaf8"
     "ramdiskId" "baz"
     "region" "us-east-1"
     "version" "2010-08-31"
     "availabilityZone" "us-east-1b"
     "privateIp" "10.201.215.38"
     "devpayProductCodes" ["bar"]}))

(defn route
  [uri]
  (cond
    (= uri "/latest/meta-data/iam/info") iam-info
    (or (u/getenv ec2-metadata-utils/container-credentials-relative-uri-env-var)
        (u/getenv ec2-metadata-utils/container-credentials-full-uri-env-var)) iam-cred
    (= uri "/latest/meta-data/iam/security-credentials/") iam-cred-list
    (re-find #"/latest/meta-data/iam/security-credentials/.+" uri) iam-cred
    (= uri "/latest/dynamic/instance-identity/document") instance-info
    :default nil))

(defn handler
  [req]
  (let [resp-body (route (:uri req))]
    {:status (if resp-body 200 404)
     :body resp-body}))

(defn start
  "Starts a ec2 metadata utils server. Returns a no-arg stop function."
  [port]
  (http-server/run-server handler {:ip "127.0.0.1" :port port}))

(comment
  (def stop-fn (start 0))

  (stop-fn)

  )
