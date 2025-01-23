package burp.sessioncookiefinder;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.core.ToolType;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.ui.contextmenu.ContextMenuEvent;
import burp.api.montoya.ui.contextmenu.ContextMenuItemsProvider;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;
import burp.api.montoya.http.message.HttpHeader;
import burp.api.montoya.http.*;
import burp.api.montoya.http.handler.HttpRequestToBeSent;
import burp.api.montoya.scanner.audit.issues.*;
import burp.api.montoya.scanner.audit.issues.AuditIssue;
import burp.api.montoya.sitemap.SiteMap;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class SessCookieContextMenuItemsProvider implements ContextMenuItemsProvider
{
	private final MontoyaApi api;
	SessCookiePersistence persistentTable = null;

	public SessCookieContextMenuItemsProvider(MontoyaApi api, SessCookiePersistence persistentTable)
	{
		this.api = api;
		this.persistentTable = persistentTable;
	}

	// function to deal with menu items
	@Override
	public List<Component> provideMenuItems(ContextMenuEvent event)
	{
		// note: currently, menu will only be available in proxy, target, repeater, and logger tabs
		if (event.isFromTool(ToolType.PROXY, ToolType.TARGET, ToolType.REPEATER, ToolType.LOGGER))
		{
			// getting array list to use later
			List<Component> menuItemList = new ArrayList<>();
			// populating array list with custom menu item
			JMenuItem findSessionCookies = new JMenuItem("Find session cookies");

			// getting the HttpRequestResponse interface from the selected request/response
			HttpRequestResponse requestResponse = event.messageEditorRequestResponse().isPresent() ? event.messageEditorRequestResponse().get().requestResponse() : event.selectedRequestResponses().get(0);
			// getting just the HttpRequest from the request/response
			HttpRequest cookieRequest = requestResponse.request();
			HttpResponse originalResponse = requestResponse.response();
			//on click -> calling the function to get the headers
			findSessionCookies.addActionListener(l -> getCookieHeader(cookieRequest, originalResponse, requestResponse));

			// adding and returning custom menu item
			menuItemList.add(findSessionCookies);
			return menuItemList;
		}

		return null;
	}
	
	// function to get the cookie header
	public List<Component> getCookieHeader(HttpRequest cookieRequest, HttpResponse originalResponse, HttpRequestResponse originalRR)
	{
		// apparently this needs to be initialized
		HttpHeader cookieHeader = null;

		// loop through the headers in the request
		for (int i = 0; i < cookieRequest.headers().size(); i++)
		{
			// check if this header is the cookie header
			if (cookieRequest.headers().get(i).name().equals("Cookie"))
			{
				// stores the cookie header
				cookieHeader = cookieRequest.headers().get(i);
				
			}
		}

		api.logging().logToOutput("=================================================Session Cookie Finder=================================================");
		
		// ensuring that we actually have a cookie header before we do anything with it
		if (cookieHeader != null)
		{
			// getting the values from the cookie header as a string
			String cookieValues = cookieHeader.value();
			// splititng the values into an array
			String[] valueArray = cookieValues.split(";");
			
			// need to store the cookie that was removed to reference it later
			String removedCookie = "";

			// looping through the array
			for (int i = 0; i < valueArray.length; i++)
			{
				String usedCookies = "";
				// looping through the array a second time -> we want to keep all cookies except for 1
				for (int k = 0; k < valueArray.length; k++)
				{
					// grabs all cookies but 1 and adds to new string
					if (k != i)
					{
						usedCookies += valueArray[k];
						usedCookies += ";";
					}	
					else // if removing cookie, notes the cookie that is removed
					{
						removedCookie = valueArray[k];
					}
				}

				// do stuff with this new string of all but 1 cookies -> creates new cookie header + sends to function that creates requests
				HttpHeader newCookies = HttpHeader.httpHeader("Cookie", usedCookies);
				sendCookieRequest(cookieRequest, newCookies, originalResponse, removedCookie, originalRR);
			}
		}
		else
		{
			api.logging().logToOutput("The cookie header was not found.");
		}

		api.logging().raiseInfoEvent("The session cookie check has completed. Please check the extension output to view any discovered session cookies.");

		return null;
	}

	
	// function to send the new http request with the edited cookies
	public List<Component> sendCookieRequest(HttpRequest cookieRequest, HttpHeader cookieHeader, HttpResponse originalResponse, String removedCookie, HttpRequestResponse originalRR)
	{
		// create a new thread so Burp doesn't get mad
		Thread t = new Thread(new Runnable()
		{
			public void run()
			{
				// create a new request with the updated cookie header
				HttpRequest newRequest = cookieRequest.withUpdatedHeader(cookieHeader);
				// send the updated request
				HttpRequestResponse requestResponse = api.http().sendRequest(newRequest);
				
				// getting the host header to use when storing the cookie
				HttpHeader hostHeader = null;
        	        	// loop through the headers in the request
                		for (int i = 0; i < cookieRequest.headers().size(); i++)
                		{
                        		// check if this header is the host header
                        		if (cookieRequest.headers().get(i).name().equals("Host"))
                        		{
                                		// stores the host header
                                		hostHeader = cookieRequest.headers().get(i);
                        		}
                		}

				// use the new response + old response to check if this was the session cookie
				HttpResponse newResponse = requestResponse.response();
				checkIfSessionCookie(newResponse, originalResponse, removedCookie, hostHeader.value(), originalRR, requestResponse);
			}
		});

		// start the thread
		t.start();

		return null;
	}

	// function to compare the new response without a cookie to the original response + determine if session cookie
	public List<Component> checkIfSessionCookie(HttpResponse newResponse, HttpResponse originalResponse, String removedCookie, String requestUrl, HttpRequestResponse originalRR, HttpRequestResponse newRR)
	{
		// keeping only the name of the cookie, not the value
		String[] cookieName = removedCookie.trim().split("=");

		//If we get a 401 Unauthorized, 403 Forbidden, or a 302 + there was not one previously, then that is the session cookie
		if  (newResponse.statusCode() == 401 || newResponse.statusCode() == 403 || newResponse.statusCode() == 302)
		{
			if (originalResponse.statusCode() != newResponse.statusCode())
			{
				api.logging().logToOutput("The cookie \"" + cookieName[0] + "\" is a session cookie! The staus code " + newResponse.statusCode() + " was received when this cookie was removed.");
				
				// adding cookie to extension UI table
				persistentTable.addPersistentCookie(requestUrl, cookieName[0], api);

				// adding session cookie as an issue
				AuditIssue cookieIssue = AuditIssue.auditIssue("Session Cookie Found", "The session cookie \"" + cookieName[0] + "\" has been identified. The status code " + newResponse.statusCode() + " was received when this cookie was removed.", null, originalRR.request().url(), AuditIssueSeverity.INFORMATION, AuditIssueConfidence.TENTATIVE, "Session cookies are used to identify a user who is authenticated to an application.", null, AuditIssueSeverity.INFORMATION, originalRR, newRR);
				api.siteMap().add(cookieIssue);
			}
		} // else if the new response doesn't match the original response -> may need to get more in depth with how different these responses are
		else if (!newResponse.bodyToString().equals(originalResponse.bodyToString()))
		{
			api.logging().logToOutput("The cookie \"" + cookieName[0] + "\" is a session cookie! The body of the response was different when this cookie was removed.");
			//api.logging().logToOutput("Unmodified body:\r\n" + originalResponse.bodyToString());
			//api.logging().logToOutput("Modified body:\r\n" + newResponse.bodyToString());
		
			// adding cookie to extension UI table
			persistentTable.addPersistentCookie(requestUrl, cookieName[0], api);

			// adding session cookie as an issue
			AuditIssue cookieIssue = AuditIssue.auditIssue("Session Cookie Found", "The session cookie \"" + cookieName[0] + "\" has been identified. The body of the response was different when this cookie was removed.", null, originalRR.request().url(), AuditIssueSeverity.INFORMATION, AuditIssueConfidence.TENTATIVE, "Session cookies are used to identify a user who is authenticated to an application.", null, AuditIssueSeverity.INFORMATION, originalRR, newRR);
			api.siteMap().add(cookieIssue);
		}
		else // the cookie was not identified as the session cookie through the previous checks -> may add additional checks to improve accuracy
		{
			api.logging().logToOutput("The cookie \"" + cookieName[0] + "\" was not determined to be a session cookie.");
		}

		return null;
	}
}
