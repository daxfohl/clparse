(ns clparse.core
  (:gen-class)
  [:require 
   [net.cgrand.enlive-html :as html]
   [clojure.pprint :as pp]
   [clojure.string :as st]
   [clj-time [core :as tm] [format :as tmf]]])

(defn currstr[x] (.format (java.text.NumberFormat/getCurrencyInstance) x))
(defrecord WorkItem [date qty task item-type unit-price details])
(defn get-total [work-item] (* (:unit-price work-item) (:qty work-item)))
(defn get-total-string [work-items] (->> work-items (map get-total) (reduce +) (currstr)))
(defn get-key [work-item] {:task (:task work-item) :item-type (:item-type work-item) :unit-price (:unit-price work-item)})

(def document (html/html-resource (java.net.URL. "file:///C:/Users/Dax/git/clparse/clparse/invoice.html")))
(def templates (html/html-resource (java.net.URL. "file:///C:/Users/Dax/git/clparse/clparse/templates.html")))
(def client-document (html/select document [:#client_document]))
(def summary-template (html/select templates [:#summary_table]))
(def item-body-template (html/select templates [:#item_body_template]))
(def page-break (html/select templates [:#breaks]))
(def breakdown-table (html/select client-document [:table.client-document-items]))
(def breakdown-body (html/select breakdown-table [:tbody.client-document-item-rows]))
(def trs (html/select breakdown-body [:tr]))  
(def date-formatter (tmf/formatter "MM/dd/yyyy"))

(defn to-item [tr]
  (def td (vec (html/select tr [:td])))
  (def item-type (html/text (nth td 0)))
  (def desc (html/text (nth td 1)))
  (def split (st/split desc #" "))
  (def date (tmf/parse date-formatter (nth split 0)))
  (def colonSplit (st/split desc #":"))
  (def task (subs (nth colonSplit 0) (+ 3 (count (nth split 0)))))
  (def details (if (> (count colonSplit) 1) (vec (map st/trim (st/split (nth colonSplit 1) #","))) [])) 
  (def qty (bigdec (html/text (nth td 2))))
  (def unit-price (bigdec (st/replace (html/text (nth td 3)) #"[^0-9\.]" "")))
  (WorkItem. date qty task item-type unit-price details))

(def items (vec (map to-item trs)))


(def summary-table
  (html/at summary-template 
           [:tr.client-document-item-rows-odd]
           (html/clone-for [tg (group-by :item-type items)]
                           [:p] (html/content (key tg))
                           [:td.client-document-item-amount] (html/content (get-total-string (val tg))))
           [:#summary_total]
           (html/content (get-total-string items))))


(def item-body
  (html/at item-body-template
           [:tr]
           (html/clone-for [tg (->> items (group-by get-key) (sort-by #(vec (map (key %) [:item-type :unit-price :task]))))]
                           [:td.client-document-item-type] (html/content (:task (key tg)))
                           [:p] (html/content (->> (val tg) (map :details) (reduce concat) (distinct) (st/join "; ")))
                           [:td.client-document-item-qty] (html/content (->> (val tg) (map :qty) (reduce +) (str)))
                           [:td.client-document-item-unit-price] (html/content (->> (key tg) (:unit-price) (currstr)))
                           [:td.client-document-item-amount] (html/content (get-total-string (val tg))))))


(def doc-with-tranform
  (html/at document 
           [:tbody.client-document-item-rows]
           (html/substitute item-body)
           [:table.client-document-items] 
           (html/before [summary-table page-break])))


(defn -main []  
  (println (apply str (html/emit* doc-with-tranform))))

