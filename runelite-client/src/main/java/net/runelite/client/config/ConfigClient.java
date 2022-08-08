/*
 * Copyright (c) 2017, Adam <Adam@sigterm.info>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package net.runelite.client.config;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import javax.inject.Inject;
import javax.inject.Named;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.http.api.RuneLiteAPI;
import net.runelite.http.api.config.ConfigPatch;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

@Slf4j
public class ConfigClient
{
	private final OkHttpClient client;
	private final HttpUrl apiBase;
	private final Gson gson;

	@Setter
	private UUID uuid;

	@Inject
	private ConfigClient(OkHttpClient client, @Named("runelite.api.base") HttpUrl apiBase, Gson gson)
	{
		this.client = client;
		this.apiBase = apiBase;
		this.gson = gson;
	}

	public Map<String, String> get() throws IOException
	{
		HttpUrl url = apiBase.newBuilder()
			.addPathSegment("config")
			.addPathSegment("v2")
			.build();

		log.debug("Built URI: {}", url);

		Request request = new Request.Builder()
			.header(RuneLiteAPI.RUNELITE_AUTH, uuid.toString())
			.url(url)
			.build();

		try (Response response = client.newCall(request).execute())
		{
			InputStream in = response.body().byteStream();
			// CHECKSTYLE:OFF
			final Type type = new TypeToken<Map<String, String>>(){}.getType();
			// CHECKSTYLE:ON
			return RuneLiteAPI.GSON.fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), type);
		}
		catch (JsonParseException ex)
		{
			throw new IOException(ex);
		}
	}

	public CompletableFuture<Void> patch(ConfigPatch patch)
	{
		CompletableFuture<Void> future = new CompletableFuture<>();

		HttpUrl url = apiBase.newBuilder()
			.addPathSegment("config")
			.addPathSegment("v2")
			.build();

		log.debug("Built URI: {}", url);

		Request request = new Request.Builder()
			.patch(RequestBody.create(RuneLiteAPI.JSON, gson.toJson(patch)))
			.header(RuneLiteAPI.RUNELITE_AUTH, uuid.toString())
			.url(url)
			.build();

		client.newCall(request).enqueue(new Callback()
		{
			@Override
			public void onFailure(Call call, IOException e)
			{
				log.warn("Unable to synchronize configuration item", e);
				future.completeExceptionally(e);
			}

			@Override
			public void onResponse(Call call, Response response)
			{
				if (response.code() != 200)
				{
					String body = "bad response";
					try
					{
						body = response.body().string();
					}
					catch (IOException ignored)
					{
					}

					log.warn("failed to synchronize some of {}/{} configuration values: {}",
						patch.getEdit().size(), patch.getUnset().size(), body);
				}
				else
				{
					log.debug("Synchronized {}/{} configuration values",
						patch.getEdit().size(), patch.getUnset().size());
				}
				response.close();
				future.complete(null);
			}
		});

		return future;
	}
}
