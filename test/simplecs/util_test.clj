(ns simplecs.util-test
  (:require [clojure.test :refer :all]
            [simplecs.core :as sc]
            [simplecs.util :refer :all]))

(def ces
  (sc/make-ces {:entities [[(assoc-in-ces :a :b)]]
                :systems [(ces-assocer)]}))

(deftest assoc-in-ces-test
  (let [advanced-ces (sc/advance-ces ces)]
    (is (= (sc/last-added-entity ces)
           (:a advanced-ces)
           (:b advanced-ces)))))
