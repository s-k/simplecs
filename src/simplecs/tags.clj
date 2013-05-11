(ns simplecs.tags
  (:require [simplecs.core :as sc]))

(defn tag
  "Instead of defining a component '(defcomponent foo [])' and
   adding '(foo)' to an entity, you can just add '(tag :foo)'
   to the entity. There MUST NOT exist a component with the
   same name as the tag keyword."
  [tag]
  {:pre [(keyword? tag)]}
  {:name tag})

(sc/defcomponent tag-to-entity [& paths]
  :paths paths)

(sc/defcomponentsystem tag-to-entity-converter :tag-to-entity
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