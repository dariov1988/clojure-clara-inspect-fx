(ns clara-inspect-fx.state
  "App state atom and default rules/facts (same example as clojure-clara-dyn-cli).")

;;; ---------------------------------------------------------------------------
;;; Default rules and facts (from clojure-clara-dyn-cli data/rules.txt and data/facts.txt)
;;; ---------------------------------------------------------------------------
(def default-rules-text
  (str ";; ============================================================================\n"
       ";; Domain Entities (Facts)\n"
       ";; ============================================================================\n\n"
       "(defrecord Customer [id name country tier total-spend])\n"
       "(defrecord OrderRequest [id customer-id items total-value shipping-cost])\n"
       "(defrecord PromoCode [code discount-type value])\n"
       "(defrecord Discount [order-id description amount])\n"
       "(defrecord ShippingDiscount [order-id description amount])\n"
       "(defrecord RiskFlag [order-id reason severity])\n"
       "(defrecord ApprovalRequired [order-id reason])\n\n"
       ";; ============================================================================\n"
       ";; Rules\n"
       ";; ============================================================================\n\n"
       ";; --- Promotion Rules ---\n\n"
       "(defrule free-shipping-over-100\n"
       "  \"Orders over $100 get free shipping.\"\n"
       "  [OrderRequest (= ?id id) (> total-value 100) (> shipping-cost 0)]\n"
       "  =>\n"
       "  (insert! (->ShippingDiscount ?id \"Free Shipping > $100\" 0)))\n\n"
       "(defrule vip-discount\n"
       "  \"VIP customers get 10% off.\"\n"
       "  [Customer (= ?cid id) (= tier :vip)]\n"
       "  [OrderRequest (= ?oid id) (= customer-id ?cid) (= ?val total-value)]\n"
       "  =>\n"
       "  (insert! (->Discount ?oid \"VIP 10% Off\" (* 0.10 ?val))))\n\n"
       "(defrule holiday-promo\n"
       "  \"Holiday promo code gives flat $20 off.\"\n"
       "  [PromoCode (= code \"HOLIDAY2026\") (= ?val value)]\n"
       "  [OrderRequest (= ?oid id)]\n"
       "  =>\n"
       "  (insert! (->Discount ?oid \"Holiday Promo\" ?val)))\n\n"
       ";; --- Risk Rules ---\n\n"
       "(defrule high-value-risk\n"
       "  \"Flag high value orders for review.\"\n"
       "  [OrderRequest (= ?id id) (> total-value 5000)]\n"
       "  =>\n"
       "  (insert! (->RiskFlag ?id \"High Value Transaction\" :medium)))\n\n"
       "(defrule international-shipping-risk\n"
       "  \"Flag international shipping for non-VIPs.\"\n"
       "  [Customer (= ?cid id) (not= country \"US\") (not= tier :vip)]\n"
       "  [OrderRequest (= ?oid id) (= customer-id ?cid)]\n"
       "  =>\n"
       "  (insert! (->RiskFlag ?oid \"International Shipping (Non-VIP)\" :low)))\n\n"
       ";; --- Approval Rules (Derived from Risk) ---\n\n"
       "(defrule require-approval-severe-risk\n"
       "  \"Require approval if there is a severe risk or multiple medium risks.\"\n"
       "  [?risk <- RiskFlag (= ?oid order-id) (= severity :high)]\n"
       "  =>\n"
       "  (insert! (->ApprovalRequired ?oid \"Severe Risk Detected\")))\n\n"
       "(defrule accumulate-discounts\n"
       "  \"Example of an accumulator or just a logical grouping to inspect.\"\n"
       "  [?order <- OrderRequest (= ?oid id)]\n"
       "  [?discounts <- (acc/all) :from [Discount (= order-id ?oid)]]\n"
       "  =>\n"
       "  nil)\n"))

(def default-facts-text
  (str "[\n"
       " ;; Customers\n"
       " {:type :Customer :id 1 :name \"Alice\" :country \"US\" :tier :vip :total-spend 1200}\n"
       " {:type :Customer :id 2 :name \"Bob\" :country \"CA\" :tier :regular :total-spend 200}\n\n"
       " ;; Orders\n"
       " {:type :OrderRequest :id 101 :customer-id 1 :items [\"Laptop\"] :total-value 1200 :shipping-cost 50}\n"
       " {:type :OrderRequest :id 102 :customer-id 2 :items [\"Book\"] :total-value 50 :shipping-cost 10}\n"
       " {:type :OrderRequest :id 103 :customer-id 2 :items [\"Gold Bar\"] :total-value 6000 :shipping-cost 100}\n\n"
       " ;; Promos\n"
       " {:type :PromoCode :code \"HOLIDAY2026\" :discount-type :flat :value 20}\n"
       "]\n"))

;;; ---------------------------------------------------------------------------
;;; Initial state
;;; ---------------------------------------------------------------------------
(defn initial-state
  []
  {:rules-text     default-rules-text
   :facts-text     default-facts-text
   :trace          []
   :output         ""
   :error          nil
   :inspect-result nil
   :root-facts     nil
   :running?       false})

(defonce *state (atom (initial-state)))
