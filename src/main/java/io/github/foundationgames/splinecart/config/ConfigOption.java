package io.github.foundationgames.splinecart.config;

import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import io.github.foundationgames.splinecart.Splinecart;
import net.minecraft.command.CommandSource;

import java.io.IOException;
import java.util.Properties;

public abstract class ConfigOption<T> {
    protected final Config owner;

    public final String key;
    protected T value;

    protected ConfigOption(String key, T initialValue, Config owner) {
        this.owner = owner;

        this.key = key;
        this.value = initialValue;
    }

    protected abstract void read(Properties properties);

    protected abstract void write(Properties properties);

    protected abstract ArgumentType<T> commandArgType();

    public final <S extends CommandSource> RequiredArgumentBuilder<S, ?> commandArg(String name) {
        return RequiredArgumentBuilder.argument(name, this.commandArgType());
    }

    public T get() {
        return this.value;
    }

    public void set(T value) {
        this.value = value;
    }

    public void setAndSave(T value) {
        this.set(value);
        try {
            this.owner.save();
        } catch (IOException e) {
            Splinecart.LOGGER.error("Error saving config '{}' while setting value '{}={}'",
                    this.owner.id, this.key, this.value, e);
        }
    }

    public abstract <S extends CommandSource> void setFromCommandAndSave(CommandContext<S> ctx, String argName);

    public static class BooleanOption extends ConfigOption<Boolean> {
        public BooleanOption(String key, Boolean initialValue, Config owner) {
            super(key, initialValue, owner);
        }

        @Override
        protected void read(Properties properties) {
            if (properties.containsKey(this.key)) {
                this.value = "true".equals(properties.getProperty(this.key));
            }
        }

        @Override
        protected void write(Properties properties) {
            properties.setProperty(this.key, this.value ? "true" : "false");
        }

        @Override
        public ArgumentType<Boolean> commandArgType() {
            return BoolArgumentType.bool();
        }

        @Override
        public <S extends CommandSource> void setFromCommandAndSave(CommandContext<S> ctx, String argName) {
            this.setAndSave(BoolArgumentType.getBool(ctx, argName));
        }
    }
}
