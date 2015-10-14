# adtech-macros

A Clojure library designed to do adtech-style (aka UNIX-style aka
DOS-style) macro expansion aka string interpolation.

## Usage

In your `project.clj`'s dependencies:

![Clojars Project](http://clojars.org/thunknyc/adtech-macros/latest-version.svg)]

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
 "BLARG" {:FROB "BLOINK"}}
```

the following macros expand thusly:

| Macro | Expansion |
| ----- | --------- |
| `${FOO}` | `:BAR` |
| `${MUMBLE.0}` | `:a` |
| `${BLARG.FROB}` | `BLOINK` |

The following macros showing undefined behavior all evaluate to the
(optionally-provided) default value:

| Invalid macro |
| ------------- |
| `${MISSING}` |
| `${MUMBLE}` |
| `${MUMBLE.10}` |
| `${MUMBLE.POW}` |
| `${BLARG.ABULATE}` |

### Indirection!

Given the collection `coll`

```clojure
{:double-indirect "indirect",
 :indirect "mumble",
 :foo [42],
 :bar {:mumble "tree"}}
 ```

rendering the template `"${foo.0}-${bar.(indirect)}"` results in the
production of `"42-tree"`.

Indirection can be nested aribitrarily, so, for example,
`"${foo.0}-${bar.((double-indirect))}"` also produces
`"42-tree"`. Full path expressions can be used as indirect keys; they
are not limited to single components.

### Aracana

For maps, all path components of a macro are first tried as keywords,
then as strings. For vectors and lists, all path components are
converted to integers. Note that given the above rules, integer map
keys are inaccessible as are string keys shadowed by a keyword with
the same name.

## License

Copyright © 2015 Edwin Watkeys

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
