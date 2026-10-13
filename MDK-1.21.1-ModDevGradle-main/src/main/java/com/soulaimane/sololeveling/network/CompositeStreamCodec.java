package com.soulaimane.sololeveling.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

import java.util.function.Function;

/**
 * Extension of StreamCodec.composite that supports up to 16 fields.
 * Required because the vanilla composite only handles 8 fields.
 */
public final class CompositeStreamCodec {

    private CompositeStreamCodec() {}

    public static <B extends FriendlyByteBuf, C, T1, T2, T3, T4, T5, T6, T7, T8, T9> StreamCodec<B, C> composite(
            final StreamCodec<? super B, T1> codec1, final Function<C, T1> getter1,
            final StreamCodec<? super B, T2> codec2, final Function<C, T2> getter2,
            final StreamCodec<? super B, T3> codec3, final Function<C, T3> getter3,
            final StreamCodec<? super B, T4> codec4, final Function<C, T4> getter4,
            final StreamCodec<? super B, T5> codec5, final Function<C, T5> getter5,
            final StreamCodec<? super B, T6> codec6, final Function<C, T6> getter6,
            final StreamCodec<? super B, T7> codec7, final Function<C, T7> getter7,
            final StreamCodec<? super B, T8> codec8, final Function<C, T8> getter8,
            final StreamCodec<? super B, T9> codec9, final Function<C, T9> getter9,
            final Function9<T1, T2, T3, T4, T5, T6, T7, T8, T9, C> factory
    ) {
        return new StreamCodec<>() {
            @Override
            public C decode(B buffer) {
                T1 t1 = codec1.decode(buffer);
                T2 t2 = codec2.decode(buffer);
                T3 t3 = codec3.decode(buffer);
                T4 t4 = codec4.decode(buffer);
                T5 t5 = codec5.decode(buffer);
                T6 t6 = codec6.decode(buffer);
                T7 t7 = codec7.decode(buffer);
                T8 t8 = codec8.decode(buffer);
                T9 t9 = codec9.decode(buffer);
                return factory.apply(t1, t2, t3, t4, t5, t6, t7, t8, t9);
            }

            @Override
            public void encode(B buffer, C object) {
                codec1.encode(buffer, getter1.apply(object));
                codec2.encode(buffer, getter2.apply(object));
                codec3.encode(buffer, getter3.apply(object));
                codec4.encode(buffer, getter4.apply(object));
                codec5.encode(buffer, getter5.apply(object));
                codec6.encode(buffer, getter6.apply(object));
                codec7.encode(buffer, getter7.apply(object));
                codec8.encode(buffer, getter8.apply(object));
                codec9.encode(buffer, getter9.apply(object));
            }
        };
    }

    @FunctionalInterface
    public interface Function9<T1, T2, T3, T4, T5, T6, T7, T8, T9, R> {
        R apply(T1 t1, T2 t2, T3 t3, T4 t4, T5 t5, T6 t6, T7 t7, T8 t8, T9 t9);
    }
}