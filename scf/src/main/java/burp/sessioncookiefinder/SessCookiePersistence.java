package burp.sessioncookiefinder;

import burp.api.montoya.BurpExtension;
import burp.api.montoya.MontoyaApi;
import burp.api.montoya.ui.UserInterface;
import burp.api.montoya.persistence.PersistedObject;
import burp.api.montoya.persistence.*;

import javax.swing.*;
import java.awt.*;
import javax.swing.table.DefaultTableModel;
import java.util.Arrays;
import java.util.ArrayList;

public class SessCookiePersistence
{
	private MontoyaApi api;

        public SessCookiePersistence(MontoyaApi api)
        {
                this.api = api;
	}

	// defining table at the class level so it is accessible in all methods
        DefaultTableModel tableModel = new DefaultTableModel();
        JTable testTable = new JTable(tableModel);

        static final String SESSION_COOKIE_KEY = "Cookie Finder";

	// function to construct UI
        public Component constructExtensionTab(MontoyaApi api)
        {
                // adding columns
                tableModel.addColumn("Id");
		tableModel.addColumn("Site");
                tableModel.addColumn("Session Cookie");

		// getting any persistent cookies if they exist
		getPersistedCookies(api);

		// creating a scroll pane so the columns show up
		JScrollPane scrollTable = new JScrollPane(testTable);

                return scrollTable;
        }

        // function to add cookie to UI table
        public Component addCookieRow(String rowId, String siteUrl, String sessionCookie)
        {
                tableModel.addRow(new Object[] {rowId, siteUrl, sessionCookie});

                return null;
        }

        // function to get stored cookies
        public void getPersistedCookies(MontoyaApi api)
        {
		this.api = api;

		// starting by clearing out the table
		while (tableModel.getRowCount() > 0)
                {
                        tableModel.removeRow(0);
                }

                PersistedObject myExtensionData = api.persistence().extensionData();
                String savedCookies = myExtensionData.getString(SESSION_COOKIE_KEY);

                // if there are actually saved cookies
                if (savedCookies != null)
                {
                        // splits up the cookies + urls from the saved string
                        String[] savedArray = savedCookies.split(",");
                        // loops through array of cookies + urls
                        for (int i = 0; i < savedArray.length; i = i + 3)
                        {
                                addCookieRow(savedArray[i], savedArray[i + 1], savedArray[i + 2]);
                        }
                }
        }

        // function to add a cookie to the persistent string
        public void addPersistentCookie(String siteUrl, String sessionCookie, MontoyaApi api)
        {
		this.api = api;

                PersistedObject myExtensionData = api.persistence().extensionData();
                String savedCookies = myExtensionData.getString(SESSION_COOKIE_KEY);
		Integer rowCount = tableModel.getRowCount();

		// possibly fixing weird bug where null gets inserted at the beginning??
		if (savedCookies == null)
		{
			savedCookies = "";
			savedCookies += rowCount.toString(); // if there are no other saved cookies, no comma is needed
		}
		else
		{
			savedCookies = savedCookies + "," + rowCount.toString(); // if there are other saved cookies, we need a comma
		}
		// adding url -> always after id so always needs a comma
		savedCookies = savedCookies + "," + siteUrl;
                // adding the cookie -> always after url so always needs a comma
                savedCookies = savedCookies + "," + sessionCookie;

                // saving
                myExtensionData.setString(SESSION_COOKIE_KEY, savedCookies);

                // we can just add this to the table now
                addCookieRow(rowCount.toString(), siteUrl, sessionCookie);
        }

	// function to delete a singluar cookie from the table
	public void deleteOneCookie(MontoyaApi api, String deleteId)
	{
		this.api = api;
		
		// catching + raising an error if a non-number is given
		try
		{
			int num = Integer.parseInt(deleteId);
			// only removing if there actually is a cookie at that id
			if (num < tableModel.getRowCount())
			{
				// it is easy to remove the row from the table
				tableModel.removeRow(num);

				// it is harder to remove the row from persistent storage
				PersistedObject myExtensionData = api.persistence().extensionData();
				String storedString = myExtensionData.getString(SESSION_COOKIE_KEY);
				// splitting the string + making a new one to hold the data without the removed cookie
				String[] stringArray = storedString.split(",");
				String newString = "";
				// go through string array
				for (int i = 0; i < stringArray.length; i = i + 3)
				{
					// if we found the data we want to delete
					if (!stringArray[i].equals(deleteId))
					{
						// making sure that the ids get updated so we don't have duplicate ids -> id is always the column number
						int thisRow = Integer.parseInt(stringArray[i]);
						if(thisRow < num)
						{
						
							newString = newString + stringArray[i] + ",";
						}
						else
						{
							newString = newString + String.valueOf(thisRow - 1) + ",";
						}
						newString = newString + stringArray[i + 1] + ",";
						newString = newString + stringArray[i + 2] + ",";
					}
				}
				// removing a trailing comma
				newString = newString.substring(0, newString.length() - 1);
				myExtensionData.setString(SESSION_COOKIE_KEY, newString);

				// if we removed any cookies, repopulate the table
				getPersistedCookies(api);
			}
			else
			{
				api.logging().logToError("There is not a saved session cookie with the specified Id.");
			}
		}
		catch (NumberFormatException e)
		{
			api.logging().logToError("The specified Id is not a number.");
		}
	}

	// function to delete all stored cookies
	public void deleteAllCookies(MontoyaApi api)
	{
		this.api = api;

		// removing cookies from table
		while (tableModel.getRowCount() > 0)
		{
			tableModel.removeRow(0);
		}

		// nulling persistent storage
		PersistedObject myExtensionData = api.persistence().extensionData();
		myExtensionData.setString(SESSION_COOKIE_KEY, null);
	}
}
