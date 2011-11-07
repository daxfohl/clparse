(ns clparse.core
  (:gen-class)
  [:require 
   [net.cgrand.enlive-html :as html]
   [clojure.pprint :as pp]
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


(defn toItem [tr]
  (def td (vec (html/select tr [:td])))
  (def tp (html/text (nth td 0)))
  (def desc (html/text (nth td 1)))
  (def split (.split desc " "))
  (def date (tmf/parse date-formatter (nth split 0)))
  (def colonSplit (.split desc ":"))
  (def task (.substring (nth colonSplit 0) (+ 3 (.length (nth split 0)))))
  ;(def works (
  task)

(def items
  (map toItem trs))

(defn -main []
  (pp/pprint items))

