package com.julesmc.subastas.utils;

import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;
import org.yaml.snakeyaml.external.biz.base64Coder.Base64Coder;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

public class SerializationUtil {

    /**
     * Serializes an ItemStack to a Base64 string.
     *
     * @param itemStack The ItemStack to serialize.
     * @return A Base64 string representing the ItemStack.
     * @throws IllegalStateException If an error occurs during serialization.
     */
    public static String itemStackToBase64(ItemStack itemStack) throws IllegalStateException {
        if (itemStack == null) return null;
        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            BukkitObjectOutputStream dataOutput = new BukkitObjectOutputStream(outputStream);
            dataOutput.writeObject(itemStack);
            dataOutput.close();
            return Base64Coder.encodeLines(outputStream.toByteArray());
        } catch (Exception e) {
            throw new IllegalStateException("Unable to save itemStack.", e);
        }
    }

    /**
     * Deserializes an ItemStack from a Base64 string.
     *
     * @param base64 The Base64 string to deserialize.
     * @return The deserialized ItemStack.
     * @throws IllegalStateException If an error occurs during deserialization.
     */
    public static ItemStack itemStackFromBase64(String base64) throws IllegalStateException {
        if (base64 == null || base64.isEmpty()) return null;
        try {
            ByteArrayInputStream inputStream = new ByteArrayInputStream(Base64Coder.decodeLines(base64));
            BukkitObjectInputStream dataInput = new BukkitObjectInputStream(inputStream);
            ItemStack itemStack = (ItemStack) dataInput.readObject();
            dataInput.close();
            return itemStack;
        } catch (ClassNotFoundException | IOException e) {
            throw new IllegalStateException("Unable to decode class type.", e);
        }
    }
}
