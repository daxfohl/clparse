(ns clparse.core
  (:gen-class)
  [:require 
   [net.cgrand.enlive-html :as html]
   [clojure.pprint :as pp]
   [clojure.string :as st]
   [clj-time [core :as tm] [format :as tmf]]])

(def templates (html/html-resource (new java.io.StringReader "
<table id='summary_table' summary='Invoice Items' class='client-document-items' cellspacing='0'>
  <thead>
    <tr>
      <th class='client-document-item-description'>Item</th>
      <th class='client-document-item-amount last'>Amount</th>
    </tr>
  </thead>
  <tbody class='client-document-item-rows' id='summary_body'>
        <tr class='client-document-item-rows-odd'>
		  <td class='client-document-item-description'><p/></td>
		  <td class='client-document-item-amount last'/>
        </tr>        
        <tr/>
		<tr>
		  <td/>
		  <td class='client-document-item-amount'> </td>
		</tr>    
		<tr class='client-document-item-rows-even'>
		  <td class='client-document-item-description'><p>Total</p></td>
		  <td class='client-document-item-amount last' id='summary_total'/>
		</tr>
  </tbody>
</table>
<div id='breaks'>
	<style>.break { page-break-before: always; }</style>
	<div class='break'/>
</div>
<tbody id='item_body_template' class='client-document-item-rows'>
	<tr>
	  <td class='client-document-item-type first'/>
	  <td class='client-document-item-description'><p/></td>
	  <td class='client-document-item-qty'/>
	  <td class='client-document-item-unit-price'/>
	  <td class='client-document-item-amount last'/>
	</tr>
</tbody>")))

(defn currstr[x] (.format (java.text.NumberFormat/getCurrencyInstance) x))
(defrecord WorkItem [date qty task item-type unit-price details])
(defn get-total [work-item] (* (:unit-price work-item) (:qty work-item)))
(defn get-total-string [work-items] (->> work-items (map get-total) (reduce +) (currstr)))
(defn get-key [work-item] {:task (:task work-item) :item-type (:item-type work-item) :unit-price (:unit-price work-item)})

(def document (html/html-resource (new java.io.File "invoice.html")))
(def summary-template (html/select templates [:#summary_table]))
(def item-body-template (html/select templates [:#item_body_template]))
(def page-break (html/select templates [:#breaks]))
(def breakdown-body (html/select document [:tbody.client-document-item-rows]))
(def trs (html/select breakdown-body [:tr]))  
(def date-formatter (tmf/formatter "MM/dd/yyyy"))

(defn to-item [tr]
  (def td (vec (html/select tr [:td])))
  (def item-type (html/text (nth td 0)))
  (def desc (html/text (nth td 1)))
  (def split (st/split desc #" "))
  (def date (tmf/parse date-formatter (nth split 0)))
  (def colon-split (st/split desc #":"))
  (def task (subs (nth colon-split 0) (+ 3 (count (nth split 0)))))
  (def details (if (> (count colon-split) 1) (vec (map st/trim (st/split (nth colon-split 1) #","))) [])) 
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
                           [:td.client-document-item-amount] (html/content (get-total-string (val tg))))           
           [[:tr html/even]] (html/add-class "client-document-item-rows-even")           
           [[:tr html/odd]] (html/add-class "client-document-item-rows-odd")))


(def doc-with-tranform
  (html/at document 
           [:tbody.client-document-item-rows]
           (html/substitute item-body)
           [:table.client-document-items] 
           (html/before [summary-table page-break])
           [:div.invoice-summary] 
           (html/html-content (str "<span>Amount Due</span>" (get-total-string items)))
           [:tr.client-document-total] 
           (html/html-content (str "<td colspan=4>Amount Due</td><td class='total'>" (get-total-string items) "</td>"))))


(defn -main []  
  (println (apply str (html/emit* doc-with-tranform)))
  (spit "invoice2.html" (apply str (html/emit* doc-with-tranform))))