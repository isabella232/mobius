/*
 * -\-\-
 * Mobius
 * --
 * Copyright (c) 2017-2020 Spotify AB
 * --
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * -/-/-
 */
package com.spotify.mobius.rx2;

import io.reactivex.Observable;
import io.reactivex.ObservableTransformer;
import io.reactivex.functions.Function;
import io.reactivex.functions.Predicate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Transformer that routes each incoming Effect descriptor to a sub-transformer associated with the
 * Effect descriptor class.
 */
class MobiusEffectRouter<F, E> implements ObservableTransformer<F, E> {

  private final MergedTransformer<F, E> mergedTransformer;

  MobiusEffectRouter(
      Set<Class<?>> handledEffectClasses,
      Collection<ObservableTransformer<F, E>> effectPerformers) {

    final Set<Class<?>> effectClasses = new HashSet<>(handledEffectClasses);
    final List<ObservableTransformer<F, E>> immutableEffectPerformers =
        Collections.unmodifiableList(new ArrayList<>(effectPerformers));

    ObservableTransformer<F, E> unhandledEffectHandler =
        new ObservableTransformer<F, E>() {
          @Override
          public Observable<E> apply(Observable<F> effects) {
            return effects
                .filter(
                    new Predicate<F>() {
                      @Override
                      public boolean test(F e) {
                        for (Class<?> effectClass : effectClasses) {
                          if (effectClass.isAssignableFrom(e.getClass())) {
                            return false;
                          }
                        }
                        return true;
                      }
                    })
                .map(
                    new Function<F, E>() {
                      @Override
                      public E apply(F e) {
                        throw new UnknownEffectException(e);
                      }
                    });
          }
        };
    List<ObservableTransformer<F, E>> allHandlers = new ArrayList<>(immutableEffectPerformers);
    allHandlers.add(unhandledEffectHandler);

    mergedTransformer = new MergedTransformer<>(allHandlers);
  }

  @Override
  public Observable<E> apply(Observable<F> effects) {

    return effects.compose(mergedTransformer);
  }
}
