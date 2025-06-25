package com.aetherauctions.util;

import com.aetherauctions.model.Bid;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;
import org.yaml.snakeyaml.external.biz.base64Coder.Base64Coder; // Using Bukkit's provided Base64Coder

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.List;
import java.util.ArrayList;

public class SerializationUtil {

    private static Gson gson;

    static {
        gson = new GsonBuilder().create();
    }

    // ItemStack a Base64
    public static String itemStackToBase64(ItemStack item) throws IllegalStateException {
        if (item == null) return null;
        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            BukkitObjectOutputStream dataOutput = new BukkitObjectOutputStream(outputStream);
            dataOutput.writeObject(item);
            dataOutput.close();
            return Base64Coder.encodeLines(outputStream.toByteArray());
        } catch (Exception e) {
            // Consider logging the error here with more details if possible
            throw new IllegalStateException("No se pudo serializar ItemStack a Base64.", e);
        }
    }

    // Base64 a ItemStack
    public static ItemStack itemStackFromBase64(String data) throws IOException {
        if (data == null || data.isEmpty()) return null;
        try {
            ByteArrayInputStream inputStream = new ByteArrayInputStream(Base64Coder.decodeLines(data));
            BukkitObjectInputStream dataInput = new BukkitObjectInputStream(inputStream);
            ItemStack item = (ItemStack) dataInput.readObject();
            dataInput.close();
            return item;
        } catch (ClassNotFoundException e) {
            throw new IOException("No se pudo deserializar ItemStack desde Base64 (clase no encontrada).", e);
        } catch (IOException e) {
            throw new IOException("No se pudo deserializar ItemStack desde Base64 (error de E/S).", e);
        }
    }

    // List<Bid> a JSON String
    public static String bidListToJson(List<Bid> bids) {
        if (bids == null || bids.isEmpty()) {
            return "[]"; // Return empty JSON array string for null or empty list
        }
        return gson.toJson(bids);
    }

    // JSON String a List<Bid>
    public static List<Bid> bidListFromJson(String json) {
        if (json == null || json.isEmpty() || json.equals("null")) { // Handle "null" string explicitly
            return new ArrayList<>();
        }
        try {
            Type bidListType = new TypeToken<ArrayList<Bid>>() {}.getType();
            List<Bid> bids = gson.fromJson(json, bidListType);
            return bids != null ? bids : new ArrayList<>();
        } catch (com.google.gson.JsonSyntaxException e) {
            // Log this error, as it indicates corrupt or invalid JSON in the DB
            // AetherAuctions.getInstance().getLogger().log(Level.WARNING, "Error al deserializar la lista de pujas desde JSON: " + json, e);
            System.err.println("Error al deserializar la lista de pujas desde JSON: " + json + " - " + e.getMessage()); // Temporary direct error
            return new ArrayList<>(); // Return empty list on error
        }
    }
}
