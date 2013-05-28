(ns simplecs.tags
  (:require [simplecs.core :as sc :refer (defcomponent defcomponentsystem)]))

(defn tag
  "Instead of defining a component '(defcomponent foo [])' and
   adding '(foo)' to an entity, you can just add '(tag :foo)'
   to the entity. There MUST NOT exist a component with the
   same name as the tag keyword."
  [tag]
  {:pre [(keyword? tag)]}
  {:name tag})

(defcomponent tag-to-entity
  "Converts a tag stored in a component into the entity containing
   the tag. The system 'tag-to-entity-converter' has to be included
   in the CES. '(tag-to-entity [:foo :bar])' reads the entry :bar
   of the component 'foo'. This entry should contain a keyword
   representing a tag. The keyword is then replaced with the
   entity which contains this tag. The 'tag-to-entity' component
   is removed after it was applied."
  [& paths]
  {:paths paths})

(defcomponentsystem tag-to-entity-converter :tag-to-entity
  "Applies the 'tag-to-entity' component. It is adviced that
   this system is the first in the list of systems."
  []
  [ces entity component]
  (let [converted-ces (reduce (fn [ces path]
                                (sc/update-entity ces
                                                  entity
                                                  path
                                                  #(first (sc/entities-with-component ces %))))
                              ces
                              (:paths component))]
    (sc/remove-component converted-ces entity :tag-to-entity)))