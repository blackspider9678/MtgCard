package com.spider.mtgcard.db.search;


import com.google.gson.*; import java.io.*; import java.nio.file.*; import java.util.*;


public final class IndexLoader {
    private final Path root; private final Gson gson = new Gson();
    public IndexLoader(Path root){ this.root = root; }
    public List<IndexRecord> loadAll() {
        Path manifest = root.resolve("index_manifest.json");
        if (!Files.exists(manifest)) return List.of();
        try (Reader r = Files.newBufferedReader(manifest)){
            JsonObject m = gson.fromJson(r, JsonObject.class); int shards = m.get("shards").getAsInt();
            List<IndexRecord> out = new ArrayList<>();
            for(int i=0;i<shards;i++){
                Path shard = root.resolve("shard_%03d.json".formatted(i));
                try (Reader sr = Files.newBufferedReader(shard)){
                    IndexRecord[] arr = gson.fromJson(sr, IndexRecord[].class); if(arr!=null) out.addAll(Arrays.asList(arr));
                }
            }
            return out;
        } catch (IOException e){ throw new RuntimeException(e); }
    }
}