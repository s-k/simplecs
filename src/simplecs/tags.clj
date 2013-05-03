(ns simplecs.tags
  (:require [simplecs.core :refer (defcomponent entities-with-component get-component)]))

(defcomponent tags [& tags]
  :tags (set tags))

(defn get-tags
  "Returns a set of all tags for the given entity id."
  [ces entity-id]
  (-> ces
      (get-component entity-id :tags)
      :tags
      (or #{})))

(defn entities-with-tag
  "Returns a set of all entities which contain the given tag."
  [ces tag]
  (->> ces
      (#(entities-with-component % :tags))
      (filter (fn [entity]
                (-> entity
                    (#(get-component ces % :tags))
                    :tags
                    (get tag))))
       set))