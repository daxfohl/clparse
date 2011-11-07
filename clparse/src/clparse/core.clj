(ns clparse.core
  (:gen-class)
  [:require 
   [net.cgrand.enlive-html :as html]
   [clojure.pprint :as pp]
   [clojure.string :as st]
   [clj-time [core :as tm] [format :as tmf]]])

(def *base-url* "file:///C:/Users/Dax/git/clparse/clparse/invoice.html")

(def document
  (html/html-resource (java.net.URL. *base-url*)))

(def breakdownTable
  (-> document
      (html/select [:div(html/attr= :summary "Invoice Items")])
      (first)))

(def breakdownBody
  (-> breakdownTable
    (html/select [:tbody])
    (first)))

(def trs 
  (html/select breakdownBody [:tr]))
  
(def date-formatter (tmf/formatter "MM/dd/yyyy"))

(defn get-works-2 [inner]
  (def splits (.split inner ","))
  (def trims (map (fn [x] (.trim x)) splits))
  (vec trims))

(defn get-works [colonSplit]
  (def ex (> (count colonSplit) 1))
  (if ex (get-works-2 (nth colonSplit 1)) []))

(defn toItem [tr]
  (def td (vec (html/select tr [:td])))
  (def tp (html/text (nth td 0)))
  (def desc (html/text (nth td 1)))
  (def split (.split desc " "))
  (def date (tmf/parse date-formatter (nth split 0)))
  (def colonSplit (.split desc ":"))
  (def task (subs (nth colonSplit 0) (+ 3 (count (nth split 0)))))
  (def works (get-works colonSplit)) 
  (def qty (bigdec (html/text (nth td 2))))
  (def unitPrice (bigdec (st/replace (html/text (nth td 3)) #"[^0-9\.]" "")))
  unitPrice)

(def items
  (vec (map toItem trs)))

(defn -main []
  (pp/pprint items))

