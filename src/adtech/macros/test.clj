(ns adtech.macros.test
  (:require  [clojure.test :as t :refer [deftest is]]
             [adtech.macros.core :as macros]))

(deftest escaping
  (is (= "XXX"
         (macros/render "${missing}" {} "XXX")))
  (is (= "%3CXXX%3E"
         (macros/render "${missing}" {} "<XXX>")))
  (is (= "<XXX>"
         (binding [macros/*filters* nil]
           (macros/render "${missing}" {} "<XXX>"))))
  (is (= "foo"
         (macros/render "${present}" {:present "foo"} "XXX")))
  (is (= "%3Cfoo%3E"
         (macros/render "${present}" {:present "<foo>"} "XXX")))
  (is (= "<foo>"
         (binding [macros/*filters* nil]
           (macros/render "${present}" {:present "<foo>"} "XXX")))))

(deftest filters
  (is (= "FOO Foo foo"
         (macros/render
          "${|upper present} ${|capitalize present} ${|lower present}"
          {:present "fOO"} "XXX"))
      (= "%3cfoo%3e"
         (binding [macros/*filters* (conj macros/*filters* :lower)]
           (macros/render "${present}" {:present "<FOO>"} "XXX")))))

(deftest nesting
  (is (= "foo"
         (macros/render "${present.a}" {:present {:a "foo"}})))
  (is (= "XXX"
         (macros/render "${present.a}" {:present {:b "foo"}} "XXX")))
  (is (= "foo"
         (macros/render "${present.0}" {:present ["foo"]})))
  (is (= "XXX"
         (macros/render "${present.1}" {:present ["foo"]} "XXX"))))

(deftest indirection
  (is (= "42"
         (macros/render "${(foo).0}" {:foo :bar :bar [42]})))
  (is (= "42"
         (macros/render "${bar.(foo)}" {:foo 0 :bar [42]})))
  (is (= "%3Cfoo%3E"
         (macros/render "${(bar.(foo))}" {:foo 0 :bar [42] :42 "<foo>"}))))

(deftest mustache
  (is (= "${FOO} 42 ${BAR}"
         (binding [macros/*style* :mustache]
           (macros/render "${FOO} {{(foo).0}} ${BAR}" {:foo :bar :bar [42]}))))
  (is (= "${42}"
         (binding [macros/*style* :mustache]
           (macros/render "${{{(foo).0}}}" {:foo :bar :bar [42]})))))

(deftest comb
  (is (= "${FOO} 42 ${BAR}"
         (binding [macros/*style* :comb]
           (macros/render "${FOO} <%= (foo).0 %> ${BAR}"
                          {:foo :bar :bar [42]}))))
  (is (= "${42}"
         (binding [macros/*style* :comb]
           (macros/render "${<%=(foo).0%>}" {:foo :bar :bar [42]})))))

(deftest render*
  (is (= "${42}"
         (macros/render* "${<%=(foo).0%>}"
                         {:foo :bar :bar [42]}
                         {:style :comb})))
  (is (= "<%3C42%3E>"
         (macros/render* "<<%=(foo).0%>>"
                         {:foo :bar :bar ["<42>"]}
                         {:style :comb
                          :filters [:url]}))))

(deftest backups
  (is (= "foo"
         (macros/render "${missing present}" {:present "foo"})))
  (is (= "bar"
         (macros/render "${missing (present)}" {:present "foo" :foo "bar"})))
  (is (= "bar"
         (macros/render "${missing (present).1}"
                        {:present "foo" :foo [42 :bar]})))
  (is (= "bar"
         (macros/render "${present.missing (present).1}"
                        {:present "foo" :foo [42 :bar]})))
  (is (= "BAR"
         (macros/render "${|upper present.missing (present).1}"
                        {:present "foo" :foo [42 :bar]})))
  (is (= "Bar"
         (macros/render
          "${|upper,capitalize present.missing (|upper present).1}"
          {:present "FOO" :FOO [42 :bar]}))))


(def ^:private phi (/ (+ 1 (Math/sqrt 5.0)) 2.0))

(defn fib [n]
  (Math/round (/ (Math/pow phi n) (Math/sqrt 5.0))))

(defn fib2 [n]
  (cond (zero? n) 0
        (= n 1) 1
        :else (+ (fib2 (dec n)) (fib2 (dec (dec n))))))
