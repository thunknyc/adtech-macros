# adtech-macros

A Clojure library designed to do adtech-style (aka UNIX-style aka
DOS-style) macros aka string interpolation.

## Usage

In your `project.clj`'s dependencies:

![Clojars Project](http://clojars.org/thunknyc/dump/latest-version.svg)

In your code:

```clojure
(require '[adtech.macros.core :as macros])
(def template "http://example.com/?magicnumber=${MAGIC_NUMBER}")
(macros/render template {:MAGIC_NUMBER 42})
```

Assuming `(macros/render template coll default)` is evaluated against a `coll`

```clojure
{:FOO :BAR
 "FOO" :MEH
 :MUMBLE [:a :b :c :d :e :f :g :h :i :j]
 :BLARG {:FROB "BLOINK"}}
```

the following macros expand thusly:

`${FOO}` -> `:BAR` 
`${MUMBLE.0}` -> `:a` 
`${BLARG.FROB}` -> `BLOINK`.

The following macros showing undefined behavior all evaluate to the
(optionally-provided) default value:

`${MISSING}` 
`${MUMBLE}` 
`${MUMBLE.10}` 
`${BLARG.ABULATE}`

## License

Copyright © 2015 Edwin Watkeys

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
