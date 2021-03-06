;; Copyright 2014 Rich Hickey. All Rights Reserved.
;;
;; Licensed under the Apache License, Version 2.0 (the "License");
;; you may not use this file except in compliance with the License.
;; You may obtain a copy of the License at
;;
;;      http://www.apache.org/licenses/LICENSE-2.0
;;
;; Unless required by applicable law or agreed to in writing, software
;; distributed under the License is distributed on an "AS-IS" BASIS,
;; WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
;; See the License for the specific language governing permissions and
;; limitations under the License.

(ns transit.roundtrip
  (:require [cognitect.transit :as t]
            [transit.generators :as gen]
            [clojure.edn :as edn]
            [clojure.data.fressian :as f])
  (:import [java.io File ByteArrayInputStream ByteArrayOutputStream EOFException]
           [com.fasterxml.jackson.core
            JsonFactory JsonParser JsonToken JsonParseException]
           org.msgpack.MessagePack
           [org.msgpack.unpacker Unpacker MessagePackUnpacker]
           [org.msgpack.type Value MapValue ArrayValue RawValue ValueType]))

(defn now [] (. System (nanoTime)))
(defn msecs [start end] (/ (double (- end start)) 1000000.0))

(defn rt
  [write-fn read-fn sz-fn]
  (fn [form]
    (let [start (now)
          tmp (write-fn form)
          mid (now)
          form2 (read-fn tmp)
          end (now)]
      {:form form2
       ;;:size (sz-fn tmp)
       :same (= form form2)
       :write (msecs start mid)
       :read (msecs mid end)})))

(defn edn-rt
  [form]
  ((rt pr-str edn/read-string #(.length %)) form))

(defn fressian-rt
  [form]
  ((rt f/write f/read #(.limit %)) form))

(defn transit-writer
  [type]
  (fn [form]
    (let [out (ByteArrayOutputStream. 10000)
          w (t/writer out type)]
      (t/write w form)
      (.toByteArray out))))

(def transit-js-writer (transit-writer :json))
(def transit-jsv-writer (transit-writer :json-verbose))
(def transit-mp-writer (transit-writer :msgpack))

(defn transit-reader
  [type]
  (fn [bytes]
    (let [r (t/reader (ByteArrayInputStream. bytes) type)]
      (t/read r))))

(def transit-js-reader (transit-reader :json))
(def transit-jsv-reader (transit-reader :json-verbose))
(def transit-mp-reader (transit-reader :msgpack))

(defn transit-js-rt
  [form]
  ((rt transit-js-writer transit-js-reader alength) form))

(defn transit-jsv-rt
  [form]
  ((rt transit-jsv-writer transit-jsv-reader alength) form))

(defn transit-mp-rt
  [form]
  ((rt transit-mp-writer transit-mp-reader alength) form))

(defn fake-js-reader
  [bytes]
  (let [jp ^JsonParser (.createJsonParser (JsonFactory.) (ByteArrayInputStream. bytes))]
    (while (.nextToken jp)
      (when (= (.getCurrentToken jp)
               JsonToken/VALUE_STRING)
        (.getText jp)))
    nil))

(defn fake-mp-reader
  [bytes]
  (let [mup ^MessagePackUnpacker (.createUnpacker (MessagePack.) (ByteArrayInputStream. bytes))]
    (try
      (while (.getNextType mup)
        (.readValue mup))
      (catch EOFException e))
    nil))

(defn transit-js-rt-fake-read
  [form]
  ((rt (transit-writer :json) fake-js-reader) form))

(defn transit-mp-rt-fake-read
  [form]
  ((rt (transit-writer :msgpack) fake-mp-reader) form))

(defn rt-raw
  [form]
  {:edn (edn-rt form)
   :fressian (fressian-rt form)
   :transit-js (transit-js-rt form)
   :transit-jsv (transit-jsv-rt form)
   :transit-mp (transit-mp-rt form)
   ;;:transit-js-fake-read (transit-js-rt-fake-read form)
   ;;:transit-mp-fake-read (transit-mp-rt-fake-read form)
   })

(defn rt-summary
  [form]
  (let [res (rt-raw form)]
    (into {} (map (fn [[k v]] [k (dissoc v :form)]) res))))

(defn rt-summary-warm
  [n form]
  (dotimes [i n]
    (prn i)
    (rt-summary form))
  (rt-summary form))

(defn avg
  [s]
  (/ (apply + s) (float (count s))))

(defn size-test
  [m n]
  (let [forms (take m (repeatedly #(take n (repeatedly transit.generators/ednable))))
        edn (avg (map :size (map edn-rt forms)))
        fressian (avg (map :size (map fressian-rt forms)))
        transit-js (avg (map :size (map transit-js-rt forms)))
        transit-mp (avg (map :size (map transit-mp-rt forms)))]
    {:edn edn
     :fressian fressian
     :transit-js transit-js
     :transit-mp transit-mp}
    ))


(comment


)


