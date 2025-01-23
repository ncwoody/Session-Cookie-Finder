package burp.sessioncookiefinder;

import burp.api.montoya.BurpExtension;
import burp.api.montoya.MontoyaApi;
import burp.api.montoya.ui.UserInterface;
import burp.api.montoya.persistence.PersistedObject;
import burp.api.montoya.persistence.*;

import javax.swing.*;
import java.awt.*;
import javax.swing.table.DefaultTableModel;

public class SessionCookieFinder implements BurpExtension
{
	private MontoyaApi api;

	// defining table at the class level so it is accessible in all methods
	//DefaultTableModel tableModel = new DefaultTableModel();
	//JTable testTable = new JTable(tableModel);

	//static final String SESSION_COOKIE_KEY = "Cookie Finder";

	SessCookiePersistence persistentTable = new SessCookiePersistence(api);

	@Override
	public void initialize(MontoyaApi api)
	{
		this.api = api;
		api.extension().setName("Session Cookie Finder");
		//api.userInterface().registerContextMenuItemsProvider(new SessCookieContextMenuItemsProvider(api));
		
		// setting the table of session cookies on bottom??
		JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
		splitPane.setRightComponent(persistentTable.constructExtensionTab(api));

		// creating text boxes for adding cookie
		JPanel panelTest = new JPanel();
		JTextField addHost = new JTextField("Session Cookie Host");
		JTextField addCookie = new JTextField("Session Cookie Cookie");
		JButton addButton = new JButton("Add session cookie");
		addButton.addActionListener(l -> persistentTable.addPersistentCookie(addHost.getText(), addCookie.getText(), api));
		panelTest.add(addHost);
		panelTest.add(addCookie);
		panelTest.add(addButton);

		// creating text boxes for deleting cookie
		JTextField deleteId = new JTextField("Session Cookie Id");
		JButton deleteButton = new JButton("Delete session cookie");
		deleteButton.addActionListener(l -> persistentTable.deleteOneCookie(api, deleteId.getText()));
		panelTest.add(deleteId);
		panelTest.add(deleteButton);

		// creating text boxes for deleting all cookies
		JButton deleteAll = new JButton("Delete all saved cookies");
		deleteAll.addActionListener(l -> persistentTable.deleteAllCookies(api));
		panelTest.add(deleteAll);

		// adding the menu on top + setting the size
		splitPane.setLeftComponent(panelTest);
		splitPane.setDividerLocation(40);
		
		// UI tab
		//api.userInterface().registerSuiteTab("Session Cookie Finder", persistentTable.constructExtensionTab(api));
		api.userInterface().registerSuiteTab("Session Cookie Finder", splitPane);

		// context menu
		api.userInterface().registerContextMenuItemsProvider(new SessCookieContextMenuItemsProvider(api, persistentTable));
	}
}
