(ns simplecs.examples.ping-pong
  (:require [simplecs.core :refer :all]))

(defcomponent ball [])

(defcomponent position [pos]
  :pos pos)

(defcomponent velocity [v]
  :v v)

(defcomponentsystem mover :velocity
  []
  [ces entity velocity-component]
  (update-component ces
                    entity
                    :position
                    #(update-in % [:pos] + (:v velocity-component))))

(defcomponentsystem collider :ball
  []
  [ces entity _]
  (let [pos (:pos (get-component ces entity :position))
        v (:v (get-component ces entity :velocity))]
    (if (or (and (< pos 1)
                 (< v 0))
            (and (>= pos (- (:width ces) 1))
                 (> v 0)))
      (update-component ces
                        entity
                        :velocity
                        #(update-in % [:v] -))
      ces)))

(defcomponentsystem position-validator :ball
  []
  [ces entity _]
  (update-component ces
                    entity
                    :position
                    (fn [component]
                      (update-in component
                                 [:pos]
                                 #(max 0 (min (- (:width ces) 1) %))))))

(defsystem output-clearer
  [bg-symbol]
  [ces]
  (assoc ces :output (vec (repeat (:width ces) bg-symbol))))

(defcomponentsystem ball-renderer :ball
  [ball-symbol]
  [ces entity _]
  (assoc-in ces
            [:output (:pos (get-component ces entity :position))]
            ball-symbol))

(defcomponentsystem ping-pong-renderer :ball
  []
  [ces entity _]
  (let [pos (:pos (get-component ces entity :position))]
    (cond (= pos 0) (assoc ces :output [\P \I \N \G \!])
          (= pos (- (:width ces) 1)) (assoc ces :output [\P \O \N \G \!])
          :default ces)))

(defsystem output-converter
  []
  [ces]
  (update-in ces [:output] #(apply str %)))

(def ces
  (atom (make-ces {:entities [[(ball)
                               (position -1)
                               (velocity 1)]]
                   :systems [(mover)
                             (collider)
                             (position-validator)
                             (output-clearer \.)
                             (ball-renderer \o)
                             (ping-pong-renderer)
                             (output-converter)]
                   :width 5})))

(defn update []
  (swap! ces advance-ces)
  (println (:output @ces)))

(dotimes [i 20] (update))
