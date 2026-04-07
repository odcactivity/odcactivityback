package com.odk.jackson;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.odk.Entity.Entite;
import com.odk.Entity.Salle;
import com.odk.Entity.TypeActivite;
import java.io.IOException;

/**
 * Le front envoie souvent des IDs bruts (nombre ou chaîne) pour les @ManyToOne, pas des objets complets.
 */
public final class JpaRefIdDeserializers {

    private JpaRefIdDeserializers() {}

    private static Long readId(JsonParser p, JsonNode node) throws IOException {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isNumber()) {
            return node.longValue();
        }
        if (node.isTextual()) {
            String s = node.asText().trim();
            if (s.isEmpty()) {
                return null;
            }
            return Long.parseLong(s);
        }
        if (node.isObject() && node.has("id")) {
            JsonNode idNode = node.get("id");
            if (idNode != null && idNode.isNumber()) {
                return idNode.longValue();
            }
            if (idNode != null && idNode.isTextual()) {
                return Long.parseLong(idNode.asText().trim());
            }
        }
        throw new IOException("Format d'ID non supporté pour la référence JPA : " + node);
    }

    public static class SalleRef extends JsonDeserializer<Salle> {
        @Override
        public Salle deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            JsonNode node = p.getCodec().readTree(p);
            Long id = readId(p, node);
            return id == null ? null : new Salle(id);
        }
    }

    public static class EntiteRef extends JsonDeserializer<Entite> {
        @Override
        public Entite deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            JsonNode node = p.getCodec().readTree(p);
            Long id = readId(p, node);
            return id == null ? null : new Entite(id);
        }
    }

    public static class TypeActiviteRef extends JsonDeserializer<TypeActivite> {
        @Override
        public TypeActivite deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            JsonNode node = p.getCodec().readTree(p);
            Long id = readId(p, node);
            return id == null ? null : new TypeActivite(id);
        }
    }
}
