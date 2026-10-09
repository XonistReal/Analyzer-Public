package dev.analyzer.automation;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

/** Temporary in-memory settings. A later external edit wins over our restoration. */
final class ScopedSettingLease implements AutoCloseable {
    private final List<Binding<?>> bindings = new ArrayList<>();
    private boolean active;

    <T> void bind(Supplier<T> read, Consumer<T> write, UnaryOperator<T> safer) {
        if (active) throw new IllegalStateException("Cannot add a setting to an active lease");
        bindings.add(new Binding<>(read, write, safer));
    }

    void apply() {
        if (active) return;
        active = true;
        try {
            for (Binding<?> binding : bindings) binding.apply();
        } catch (RuntimeException | Error failure) {
            close();
            throw failure;
        }
    }

    boolean active() { return active; }
    int changedSettings() {
        if (!active) return 0;
        return (int) bindings.stream().filter(binding -> binding.applied && !Objects.equals(binding.before, binding.temporary)).count();
    }

    @Override public void close() {
        if (!active) return;
        active = false;
        for (int i = bindings.size() - 1; i >= 0; i--) bindings.get(i).restore();
    }

    private static final class Binding<T> {
        private final Supplier<T> read;
        private final Consumer<T> write;
        private final UnaryOperator<T> safer;
        private T before, temporary;
        private boolean applied;

        Binding(Supplier<T> read, Consumer<T> write, UnaryOperator<T> safer) {
            this.read = Objects.requireNonNull(read);
            this.write = Objects.requireNonNull(write);
            this.safer = Objects.requireNonNull(safer);
        }
        void apply() {
            before = read.get();
            temporary = safer.apply(before);
            applied = true;
            write.accept(temporary);
        }
        void restore() {
            if (!applied) return;
            applied = false;
            if (Objects.equals(read.get(), temporary)) write.accept(before);
        }
    }
}
