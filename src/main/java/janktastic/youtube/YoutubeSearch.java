package janktastic.youtube;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.youtube.YouTube;
import com.google.api.services.youtube.model.SearchListResponse;
import com.google.api.services.youtube.model.SearchResult;

public class YoutubeSearch {

	public final HttpTransport HTTP_TRANSPORT = new NetHttpTransport();

	public final JsonFactory JSON_FACTORY = new GsonFactory();

	public String apiKey;

	private YouTube youtube;

	public YoutubeSearch(String apiKey) {
		this.apiKey = apiKey;
		youtube = new YouTube.Builder(HTTP_TRANSPORT, JSON_FACTORY, new HttpRequestInitializer() {
			public void initialize(HttpRequest request) throws IOException {
			}
		}).setApplicationName("JankBot").build();
	}

	public SearchListResponse search(String queryTerm) {

		try {
			YouTube.Search.List search = youtube.search().list("id,snippet");
			search.setKey(apiKey);
			search.setQ(queryTerm);
			search.setType("video");
			search.setFields("items(id(videoId),snippet(title,description,thumbnails/default/url))");
			search.setMaxResults((long) 10);
			// search.setOrder(SearchSetting.getInstance().getOrder());
			// search.setSafeSearch(SearchSetting.getInstance().getSafeSearch());
			// search.setVideoDefinition(SearchSetting.getInstance().getVideoDefinition());
			// search.setVideoDuration(SearchSetting.getInstance().getVideoDuration());
			// search.setVideoType(SearchSetting.getInstance().getVideoType());
			return search.execute();
		} catch (GoogleJsonResponseException e) {
			System.out.println(
					"There was a service error: " + e.getDetails().getCode() + " : " + e.getDetails().getMessage());
		} catch (IOException e) {
			System.out.println("There was an IO error: " + e.getCause() + " : " + e.getMessage());
		} catch (Throwable t) {
			System.out.println(t);
		}

		return null;
	}
	
	public Map<String, String> getIdTitleMap(SearchListResponse searchListResponse) {
		List<SearchResult> items = searchListResponse.getItems();
		Map<String, String> idTitleMap = new LinkedHashMap<>();
		for (SearchResult item : items) {
			idTitleMap.put(item.getId().getVideoId(), item.getSnippet().getTitle());
		}
		return idTitleMap;
	}
	
	public String getVideoUrl(String videoId) {
		return "https://www.youtube.com/watch?v=" + videoId;
	}
}
