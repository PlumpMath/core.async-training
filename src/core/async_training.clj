(ns core.async-training
  (:use clojure.core.async))

(in-ns 'user)
(use 'clojure.core.async)

;; This file contains exercises in the form of expressions you should
;; be able to evaluate interactively at an EMACS repl. With many EMACS
;; environments using CIDER, getting set up with these exercises should be as:
;;
;; 1. Hitting M-x and typing cider-jack-in
;; 2. Hitting C-c C-k after the REPL prompt loads
;; 3. Placing point at the end of examples and hitting C-x M-e
;;
;; This should dump the result of the last form to your open repl
;;
;; Of course, any mechanism for evaluating the form before point
;; should work, such as C-x C-e for inferior lisp, or copying entire
;; forms and entering them at the REPL.
;;
;; All examples are in a comment block to prevent them loading when
;; this file is compiled.

(comment
  ;; Core.async is founded on the basis of channels You'll always have
  ;; a channel if you're doing Core.async Let's make a simple channel
  ;; with a 1-value buffer, write to it, then read from it.

  (def c0 (chan 1))
  ;;=> #'core.async-training/c0
  (>!! c0 :zot)
  ;;=> true
  (<!! c0)
  ;;=> :zot

  ;; Keep in mind that the operation of writing with >!! will block
  ;; the executing thread until it can complete. Channels
  ;; restrictively accept values, either by having a buffer, or by
  ;; having a reader ready to accept the value. The write above
  ;; completed because c0 has a buffer accomodating 1 value.  Note the
  ;; following will BLOCK indefinitely, you can interrupt the
  ;; evaluation with M-x cider-interrupt.

  (def c1 (chan))
  ;;=> #'core.async-training/c1
  (>!! c1 :zot)
  ;;(blocks indefinitely)

  ;; We have other ways of writing, though. put! will write to a
  ;; channel, invoking a callback you give it once the write
  ;; completes. Under the covers, >!! is implemented in terms of
  ;; put!. Try to put! :zot on c1, and then read from it.
  ;;
  ;; Note: You can omit the callback function, but do not pass nil as
  ;; your callback function.

  (def c2 (chan))
  ;;=> #'core.async-training/c2
  (put! c2 :zot)
  ;;=> true
  (<!! c2)
  ;;=> :zot

  ;; Just as >!! blocks the current thread until a write can complete,
  ;; <!! blocks a write until a read can complete:

  (def c3 (chan))
  ;;=> #'core.async-training/c3
  (<!! c3)
  ;;(blocks indefinitely)

  ;; Just as we have put!, we also have take!. take! accepts a channel
  ;; and a callback, and will invoke the callback with the first value
  ;; available on the channel.

  (def c4 (chan 1))
  ;;=> #'core.async-training/c4

  (>!! c4 :zot)
  ;;=> true

  (let [p (promise)]
    (take! c4 #(deliver p %))
    @p)
  ;;=> :zot

  ;; This is very similar to how <!! is implemented in core.async!
  ;; Have a look for yourself:
  ;; https://github.com/clojure/core.async/blob/master/src/main/clojure/clojure/core/async.clj#L77

  ;; You are not allowed to write nil on channels. Why not?  nil is a
  ;; sentinel value that indicates a channel has been closed. Once you
  ;; close a channel, all previously queued puts or takes may
  ;; complete. New puts can be accepted for queued readers. All
  ;; previously buffered values may be read, new puts will return
  ;; false unless they are completing an already enqueued operation,
  ;; and after all pending writes are completed, further reads will
  ;; return nil:

  (def c5 (chan 1))
  ;;=> #'core.async-training/c5
  (>!! c5 :zot)
  ;;=> true
  (close! c5)
  ;;=> nil
  (>!! c5 :bar)
  ;;=> false, :bar was not written, c5 was closed
  (<!! c5)
  ;;=> :zot
  (<!! c5)
  ;;=> nil
  (<!! c5)
  ;;=> nil

  ;; Reading c5 will always produce nil now!

  ;; With callbacks, we can create asynchronously invoked
  ;; pipelines. Some of this may seem familiar to you if you've ever
  ;; programmed in javascript. Let's create a pipeline that, one time,
  ;; accepts a value on c6, doubles it, and outputs the result on c7:

  (def c6 (chan 1))
  ;;=> #'core.async-training/c6
  (def c7 (chan))
  ;;=> #'core.async-training/c7
  (take! c6 #(put! c7 (* 2 %)))
  ;;=> nil
  (>!! c6 7)
  ;;=> true
  (<!! c7)
  ;;=> 14

  ;; Let's talk about javascript in a browser some more. If you've
  ;; ever worked with javascript and callbacks in the browser, they
  ;; can get absurdly deep. It's colloquially referred to as callback
  ;; hell. Conceptually we want to encode a (non-system) process
  ;; predicated on one or more input or output events occurring, and
  ;; make sequencing guarantees about when things are done. For
  ;; example, we don't want to double a value from c6 before we read
  ;; it!

  ;; One of the coolest parts of core.async is go. Go lets you write a
  ;; block with blocking semantics in mind, and it will transform it
  ;; into a sequence of callbacks for you with the same
  ;; semantics. Let's try the above example in a go block with c8 and
  ;; c9:

  (def c8 (chan 1))
  ;;=> #'core.async-training/c8
  (def c9 (chan))
  ;;=> #'core.async-training/c9
  (go (let [val (<! c8)]
        (>! c9 (* 2 val))))
  ;;=> #<ManyToManyChannel clojure.core.async.impl.channels.ManyToManyChannel@68fb71f2>
  (>!! c8 7)
  ;;=> true
  (<!! c9)
  ;;=> 14

  ;; Same semantics! Different expression. A go block lets you write
  ;; in terms of the sequence of events, without having to get into
  ;; details about nesting. It's a very powerful tool.

  ;; Additionally note the call to go returned a channel. We'll call
  ;; it The Go Channel. The Go Channel can be read, and when the go
  ;; block runs to completion, the value the last expression of the go
  ;; block evaluated to will be written to The Go Channel.

  ;; Core.async, and go particularly, is a useful tool for creating
  ;; data pipelines. You can put inputs in one end, and outputs come
  ;; out the other as their computation completes. In this case you
  ;; want the encoded go processes to run for the life of the
  ;; process. You might write something like:
  ;;
  ;; (go (loop [] ... some async stuff))
  ;;
  ;; This is so frequently done that there's a go-loop macro in core
  ;; async for convenience. Let's take our above data pipeline and
  ;; make it run forever:

  (def c10 (chan 1))
  ;;=> #'core.async-training/c10
  (def c11 (chan))
  ;;=> #'core.async-training/c11
  (go-loop [val (<! c10)]
           (>! c11 (* 2 val))
           (recur (<! c10)))
  ;;=> #<ManyToManyChannel clojure.core.async.impl.channels.ManyToManyChannel@704c6d5c>
  (>!! c10 7)
  ;;=> true
  (>!! c10 8)
  ;;=> true
  (<!! c11)
  ;;=> 14
  (<!! c11)
  ;;=> 16

  ;; The go-loop establishes where recur will jump back to. We prime
  ;; the pump reading the first value from c10, then recur only after
  ;; reading from c10 again.

  ;; Caveats!
  ;;
  ;; First: (go ...) and its variants do a good deal of munging of
  ;; their bodies to work their magic. This hasn't resulted in any
  ;; breaking logic that we're aware of yet. But it DOES result in the
  ;; bodies of go blocks being difficult to debug at run-time, with
  ;; incomprehensible stack traces. I strongly advise you to make the
  ;; bodies of go blocks very, very dumb and only concerned with i/o
  ;; for the channels relevant to that go block.  Concentrate as much
  ;; brains as you can in functions called by the go block, this will
  ;; give you better stack traces for locating problems in your
  ;; interesting logic. I think this principle makes sense even aside
  ;; from the debugging issues.
  ;;
  ;; Second: You may end up creating (go-loop ...) systems which feed
  ;; back into themselves, directly or indirectly. In these cases you
  ;; need to contemplate back pressure and positive feedback. If one
  ;; value traversing a channel ultimately provokes 2 values to
  ;; traverse that channel later due to this feedback, you have an
  ;; exponential growth scenario! You need to have a terminating
  ;; condition!  In ClojureScript, you can end up causing a dead-lock
  ;; by having too many pending reads or writes due to positive
  ;; feedback, and your whole system will grind to a halt.

  ;; Let's explore some more.

  ;; Sometimes your communicating sequential processes might want to
  ;; receive data from more than one channel. In some of these cases,
  ;; you won't care about which channel you read from, so long as you
  ;; get a value and can start processing it. This is where alts! and
  ;; friends are handy. alts! lets you describe a set of operations
  ;; you'd like to complete, and will do at most one of them.

  (def c12 (chan 1))
  ;;=> #'core.async-training/c12
  (def c13 (chan 1))
  ;;=> #'core.async-training/c13
  (def c14 (chan))
  ;;=> #'core.async-training/c14

  (go (let [[val port] (alts! [c12 c13])]
        (>! c14 val)))
  ;;=> #<ManyToManyChannel clojure.core.async.impl.channels.ManyToManyChannel@2341952a>

  (>!! c13 :harriet)
  ;;=> true

  (<!! c14)
  ;;=> :harriet

  ;; In this case we know that c13's value will end up on c14, it is
  ;; the first channel that can complete, and so it does.  If we had
  ;; values to read on both c12 and c13 prior to executing alts!, then
  ;; core.async non-deterministically would choose one of them to read
  ;; from. This tool affords you the opportunity to avoid blocking;
  ;; when you have multiple viable sources of input, pick between them
  ;; with alts! to get the one that's ready soonest.

  ;; Speaking of time, we have facilities for incorporating clock
  ;; events into your core.async systems. (timeout) will accept a
  ;; long, and will immediately return a channel. After at least the
  ;; specified number of milliseconds have passed, the channel will be
  ;; closed, returning nil on future reads.

  (def timeout-c (timeout 1000))
  ;;=> #'core.async-training/timeout-c

  (def c15 (chan))
  ;;=> #'core.async-training/c15

  (alts!! [timeout-c c15])
  ;;=> [nil #<ManyToManyChannel clojure.core.async.impl.channels.ManyToManyChannel@7b7becd8>]

  timeout-c
  ;;=> #<ManyToManyChannel clojure.core.async.impl.channels.ManyToManyChannel@7b7becd8>

  ;; Timeout can be a great tool for giving your alts! blocks a time
  ;; based escape hatch. Alternatively, you can also provide a
  ;; :default option to alts!, which will be returned if no operation
  ;; is ready. It would be extremely silly to mix doing the two, however.
)
