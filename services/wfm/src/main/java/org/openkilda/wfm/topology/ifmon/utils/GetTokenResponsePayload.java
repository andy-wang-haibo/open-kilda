package org.openkilda.wfm.topology.ifmon.utils;

import static com.google.common.base.MoreObjects.toStringHelper;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

@JsonSerialize
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder(value = { "scope", "token_type", "expires_in", "refresh_token", "access_token" })
public class GetTokenResponsePayload {

	@JsonProperty("scope")
	private String scope;

	@JsonProperty("token_type")
	private String tokenType;

	@JsonProperty("expires_in")
	private int expiresIn;

	@JsonProperty("refresh_token")
	private String refreshToken;

	@JsonProperty("access_token")
	private String accessToken;

	@JsonCreator
	public GetTokenResponsePayload(@JsonProperty("scope") final String scope,
			@JsonProperty("token_type") final String tokenType, @JsonProperty("expires_in") int expiresIn,
			@JsonProperty("refresh_token") final String refreshToken,
			@JsonProperty("access_token") String accessToken) {
		this.scope = scope;
		this.tokenType = tokenType;
		this.expiresIn = expiresIn;
		this.refreshToken = refreshToken;
		this.accessToken = accessToken;
	}

	public String getScope() {
		return scope;
	}

	public void setScope(String scope) {
		this.scope = scope;
	}

	public String getTokenType() {
		return tokenType;
	}

	public void setTokenType(String tokenType) {
		this.tokenType = tokenType;
	}

	public int getExpiresIn() {
		return expiresIn;
	}

	public void setExpiresIn(int expiresIn) {
		this.expiresIn = expiresIn;
	}

	public String getRefreshToken() {
		return refreshToken;
	}

	public void setRefreshToken(String refreshToken) {
		this.refreshToken = refreshToken;
	}

	public String getAccessToken() {
		return accessToken;
	}

	public void setAccessToken(String accessToken) {
		this.accessToken = accessToken;
	}

	@Override
	public String toString() {
		return toStringHelper(this).add("scope", scope).add("token_type", tokenType).add("expires_in", expiresIn)
				.add("refresh_token", refreshToken).add("access_token", accessToken).toString();
	}

}
