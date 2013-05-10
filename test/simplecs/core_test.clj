(ns simplecs.core-test
  (:require [clojure.test :refer :all]
            [simplecs.core :refer :all]))

(defcomponent test-component []
  :test 1)

(def ces
  (make-ces {:entities [[(test-component)]]
             :systems []}))

(deftest update-component-test
  (let [entity (last-added-entity-id ces)]
    (is (= (update-in-component ces
                                entity
                                [:test-component :test]
                                + 2)
           (update-in-component ces
                                entity
                                [:test-component]
                                #(update-in % [:test] + 2))
           (update-entity ces
                          entity
                          [:test-component :test]
                          + 2)
           (update-entity ces
                          entity
                          [:test-component]
                          #(update-in % [:test] + 2))
           (update-entity ces
                          entity
                          :test-component
                          #(update-in % [:test] + 2))
           (update-component ces
                             entity
                             :test-component
                             #(update-in % [:test] + 2))))))

(deftest letc-test
  (let [entity (last-added-entity-id ces)]
    (letc ces entity [component [:test-component]
                      test-entry [:test-component :test]]
          (is (= (:test component)
                 test-entry
                 (get-in-component ces entity [:test-component :test]))))))
