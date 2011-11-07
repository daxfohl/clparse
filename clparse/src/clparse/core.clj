(ns clparse.core
  (:gen-class)
  [:require 
   [net.cgrand.enlive-html :as html]
   [clojure.pprint :as pp]
   [clojure.string :as st]
   [clj-time [core :as tm] [format :as tmf]]])

(defrecord WorkItem [date qty task typ unitPrice works])

(def *base-url* "file:///C:/Users/Dax/git/clparse/clparse/invoice.html")

(def document
  (html/html-resource (java.net.URL. *base-url*)))

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

(def summaryTable
  '({:tag :table,
     :attrs {:summary "Invoice Summary",
             :class "client-document-items",
             :cellspacing "0"}
     :content ({:tag :thead,
                :content ({:tag :tr,
                           :content ({:tag :th,
                                      :attrs {:class "client-document-item-description"}
                                      :content ("Item")}
                                     {:tag :th,
                                      :attrs {:class "client-document-item-amount last"}
                                      :content ("Amount")}
                                      )})}
                {:tag :tbody,
                 :attrs {:class "client-document-item-rows",
                         :id "summary_body"}})}))

(defn summaryRow [desc amt]
  '({:tag :tr,
     :attrs {:class "client-document-item-rows-odd"}
             :content ({:tag :td,
                        :attrs {:class "client-document-item-description"}
                        :content (desc)}
                       {:tag :tr,
                        :attrs {:class "client-document-item-amount last"}
                        :content (amt)}
                        )}))
  
(defn applyToSum [docu taskGroup]
  (def desc (key taskGroup))
  (def amt (reduce + (range 10)));(map taskGroup (fn [grp] (* (:qty grp) (:unitPrice grp))))))
  (html/transform docu [:#summary_body] (html/append (summaryRow desc amt))))
  ;docu)


(defn appendToSummary [docu]
  (def taskGroups (group-by :task items))
  (reduce applyToSum docu taskGroups))
  

(def docWithTransform
  (appendToSummary (html/transform document [:#client_document] (html/prepend summaryTable))))


(defn -main []
  ;(pp/pprint (nth(html/select docWithTransform [:table]) 2)))  
  (println (apply str (html/emit* docWithTransform))))

