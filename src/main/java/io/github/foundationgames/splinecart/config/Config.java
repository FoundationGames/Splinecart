package io.github.foundationgames.splinecart.config;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.command.CommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Properties;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

public class Config extends ArrayList<ConfigOption<?>> {
    public static final String VALUE_SET_KEY = "splinecart.config.set_value";
    public static final String VALUE_QUERY_KEY = "splinecart.config.query_value";

    public final String id;
    public final Supplier<Path> path;

    public Config(String id, Supplier<Path> path) {
        this.id = id;
        this.path = path;
    }

    public <S extends CommandSource> LiteralArgumentBuilder<S> command(LiteralArgumentBuilder<S> cmd, BiConsumer<S, Text> feedbackSender) {
        for (var opt : this) {
            cmd.then(
                    LiteralArgumentBuilder.<S>literal(opt.key)
                            .then(
                                    opt.<S>commandArg("value").executes(context -> {
                                        opt.setFromCommandAndSave(context, "value");
                                        feedbackSender.accept(context.getSource(),
                                                Text.translatable(VALUE_SET_KEY, opt.key, opt.get()));
                                        return 0;
                                    })
                            ).executes(context -> {
                                var descKey = String.format("splinecart.config.%s.%s.desc", this.id, opt.key);
                                feedbackSender.accept(context.getSource(),
                                        Text.translatable(VALUE_QUERY_KEY, opt.key, opt.get()));
                                feedbackSender.accept(context.getSource(),
                                        Text.translatable(descKey).formatted(Formatting.GRAY));
                                return 0;
                            })
            );
        }

        return cmd;
    }

    public <V, O extends ConfigOption<V>> O opt(O opt) {
        this.add(opt);
        return opt;
    }

    public ConfigOption.BooleanOption optBool(String key, boolean value) {
        return this.opt(new ConfigOption.BooleanOption(key, value, this));
    }

    public ConfigOption.IntOption optInt(String key, int value) {
        return this.opt(new ConfigOption.IntOption(key, value, new int[0], this));
    }

    public ConfigOption.IntOption optInt(String key, int value, int min) {
        return this.opt(new ConfigOption.IntOption(key, value, new int[] {min}, this));
    }

    public ConfigOption.IntOption optInt(String key, int value, int min, int max) {
        return this.opt(new ConfigOption.IntOption(key, value, new int[] {min, max}, this));
    }

    public void load() throws IOException {
        var path = this.path.get();

        if (!Files.exists(path)) {
            return;
        }

        try (var in = Files.newInputStream(path)) {
            var properties = new Properties();
            properties.load(in);

            for (var opt : this) {
                opt.read(properties);
            }
        }
    }

    public void save() throws IOException {
        var path = this.path.get();

        if (!Files.exists(path)) {
            Files.createDirectories(path.getParent());
        }

        try (var out = Files.newOutputStream(path)) {
            var properties = new Properties();

            for (var opt : this) {
                opt.write(properties);
            }

            properties.store(out, this.id);
        }
    }
}
