(ns simplecs.util
  (:require [simplecs.core :refer (defcomponent defcomponentsystem remove-component)]))

(defcomponent assoc-in-ces
  "Tells the 'ces-assocer' system to assoc the entity
   containing the component in the CES under all of
   the given keys. The 'assoc-in-ces' component is
   removed after it was applied."
  [& ks]
  {:keys ks})

(defcomponentsystem ces-assocer :assoc-in-ces
  "Applies the 'assoc-in-ces' component. It is adviced that
   this system is one of the first in the list of systems."
  []
  [ces entity component]
  (-> (reduce #(assoc %1 %2 entity) ces (:keys component))
      (remove-component entity :assoc-in-ces)))