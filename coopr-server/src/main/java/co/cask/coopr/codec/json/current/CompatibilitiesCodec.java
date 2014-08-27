/*
 * Copyright © 2012-2014 Cask Data, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package co.cask.coopr.codec.json.current;

import co.cask.coopr.codec.json.AbstractCodec;
import co.cask.coopr.spec.template.Compatibilities;
import com.google.common.reflect.TypeToken;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;

import java.lang.reflect.Type;
import java.util.Set;

/**
 * Codec for serializing/deserializing a {@link Compatibilities}.
 */
public class CompatibilitiesCodec extends AbstractCodec<Compatibilities> {

  @Override
  public JsonElement serialize(Compatibilities compatibilities, Type type, JsonSerializationContext context) {
    JsonObject jsonObj = new JsonObject();

    jsonObj.add("hardwaretypes", context.serialize(compatibilities.getHardwaretypes()));
    jsonObj.add("imagetypes", context.serialize(compatibilities.getHardwaretypes()));
    jsonObj.add("services", context.serialize(compatibilities.getHardwaretypes()));

    return jsonObj;
  }

  @Override
  public Compatibilities deserialize(JsonElement json, Type type, JsonDeserializationContext context)
    throws JsonParseException {
    JsonObject jsonObj = json.getAsJsonObject();

    return Compatibilities.builder()
      .setHardwaretypes(context.<Set<String>>deserialize(jsonObj.get("hardwaretypes"),
                                                         new TypeToken<Set<String>>() {}.getType()))
      .setImagetypes(context.<Set<String>>deserialize(jsonObj.get("imagetypes"),
                                                      new TypeToken<Set<String>>() {}.getType()))
      .setServices(context.<Set<String>>deserialize(jsonObj.get("services"),
                                                    new TypeToken<Set<String>>() {}.getType()))
      .build();
  }
}
