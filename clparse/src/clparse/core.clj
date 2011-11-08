(ns clparse.core
  (:gen-class)
  [:require 
   [net.cgrand.enlive-html :as html]
   [clojure.pprint :as pp]
   [clojure.string :as st]
   [clj-time [core :as tm] [format :as tmf]]])

(defrecord WorkItem [date qty task typ unitPrice works])

(def document
  (html/html-resource (java.net.URL. "file:///C:/Users/Dax/git/clparse/clparse/invoice.html")))

(def summaryTable
  (html/select           
           (html/html-resource (java.net.URL. "file:///C:/Users/Dax/git/clparse/clparse/summary_table.html"))
           [:#summary_table]))


(def client-document
  (-> document
      (html/select [:#client_document])
      (first)))

(def breakdownTable
  (-> client-document
      (html/select [:div(html/attr= :summary "Invoice Items")])
      (first)))

(def breakdownBody
  (-> breakdownTable
    (html/select [:tbody])
    (first)))

(def trs 
  (html/select breakdownBody [:tr]))
  
(def date-formatter (tmf/formatter "MM/dd/yyyy"))

(defn get-works [colonSplit]
	(defn get-works-2 [inner]
	  (def splits (.split inner ","))
	  (def trims (map (fn [x] (.trim x)) splits))
	  (vec trims))
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
  (WorkItem. date qty task tp unitPrice works))

(def items
  (vec (map toItem trs)))


(defn get-total [workitem]
  (* (:unitPrice workitem) (:qty workitem)))

(def fullSummary
  (html/at summaryTable 
           [:tr.client-document-item-rows-odd]
           (html/clone-for [tg (group-by :typ items)]
                           [:p]                              (html/content (key tg))
                           [:td.client-document-item-amount] (html/content (->> (val tg)
                                                                             (map get-total)
                                                                             (reduce +)
                                                                             (str))))
           [:#summary_total]
           (html/content (->> items
                           (map get-total)
                           (reduce +)
                           (str)))))
  

(def docWithTransform
  (html/transform document [:div(html/attr= :summary "Invoice Items")] (html/before fullSummary)))


(defn -main []  
  ;(pp/pprint (html/select docWithTransform [:#summary_table])))
  (println (apply str (html/emit* docWithTransform))))

