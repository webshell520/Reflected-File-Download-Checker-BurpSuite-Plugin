package burp;
import burp.about;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.TextArea;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.net.URL;
import javax.swing.*;

public class BurpExtender implements IBurpExtender, ITab, IScannerCheck {

    Boolean isDebugging = false;
    private IBurpExtenderCallbacks callbacks;
    private IExtensionHelpers helpers;

    private PrintWriter mStdOut;
    private PrintWriter mStdErr;

    private static final byte[] INJ_TEST = "\"||calc||".getBytes();
    private static final byte[] INJ_ERROR = "\"||calc||".getBytes();
    // GUI
    private JTabbedPane topTabs;
    TextArea parametersTextArea = new TextArea();
    TextArea payloadsTextArea = new TextArea();

    //
    // implement IBurpExtender
    //
    @Override
    public void registerExtenderCallbacks(final IBurpExtenderCallbacks callbacks) {
        this.callbacks = callbacks;

        this.helpers = callbacks.getHelpers();

        this.mStdOut = new PrintWriter(callbacks.getStdout(), true);
        this.mStdErr = new PrintWriter(callbacks.getStderr(), true);

        callbacks.setExtensionName("Reflected File Download Checker");

        // register ourselves as a custom scanner check
        callbacks.registerScannerCheck(this);

        // GUI
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                topTabs = new JTabbedPane();
                JPanel parametersPanel = new JPanel();
                JPanel payloadsPanel = new JPanel();
                JPanel aboutPanel = new JPanel();
                parametersTextArea.setText("callback\njsonpcallback\njsonp\ncb\njcb");
                payloadsTextArea.setText("\"||calc||");
                topTabs.addTab("RFD Parameters", parametersPanel);
                topTabs.addTab("RFD Payloads", payloadsPanel);
                burp.about.initializeFunction(topTabs);
                parametersPanel.add(new JLabel("Parameters:"));
                parametersPanel.add(parametersTextArea);
                JButton clearButtonForParameters = new JButton("Clear");
                parametersPanel.add(clearButtonForParameters);
                clearButtonForParameters.addActionListener(new ActionListener() {
                    @Override
                    public void actionPerformed(ActionEvent ae) {
                        parametersTextArea.setText("");
                    }
                });
                payloadsPanel.add(new JLabel("Payloads:"));
                payloadsPanel.add(payloadsTextArea);
                JButton clearButtonForPayloads = new JButton("Clear");
                payloadsPanel.add(clearButtonForPayloads);
                clearButtonForPayloads.addActionListener(new ActionListener() {
                    @Override
                    public void actionPerformed(ActionEvent ae) {
                        payloadsTextArea.setText("");
                    }
                });
                // customize our UI components
                //callbacks.customizeUiComponent(topTabs); // disabled to be able to drag and drop columns
                // add the custom tab to Burp's UI
                callbacks.addSuiteTab(BurpExtender.this);
            }
        });
    }

    @Override
    public String getTabCaption() {
        return "RFD Checker Options";
    }

    @Override
    public Component getUiComponent() {
        return topTabs;
    }

    // helper method to search a response for occurrences of a literal match string
    // and return a list of start/end offsets
    private List<int[]> getMatches(byte[] response, byte[] match) {
        List<int[]> matches = new ArrayList<int[]>();

        int start = 0;
        while (start < response.length) {
            start = helpers.indexOf(response, match, true, start, response.length);
            if (start == -1) {
                break;
            }
            matches.add(new int[]{start, start + match.length});
            start += match.length;
        }

        return matches;
    }

    //
    // implement IScannerCheck
    //
    @Override
    public List<IScanIssue> doPassiveScan(IHttpRequestResponse baseRequestResponse) {
        String parametersFromTextAreaActive[] = parametersTextArea.getText().split("\\n");
        for (int i = 0; i < parametersFromTextAreaActive.length; i = i + 1) {
            if (this.helpers.analyzeRequest(baseRequestResponse).getMethod().equals("GET")) {
                List responseArray = this.helpers.analyzeResponse(baseRequestResponse.getResponse()).getHeaders();
                Iterator headerItr = responseArray.iterator();
                while (headerItr.hasNext()) {
                    String header = headerItr.next().toString();
                    if (header.contains("Content-Type:")) {
                        if ((header.contains("json")) || (header.contains("javascript"))) {
                            List parameters = this.helpers.analyzeRequest(baseRequestResponse).getParameters();
                            Iterator parameterItr = parameters.iterator();
                            while (parameterItr.hasNext()) {
                                IParameter parameter = (IParameter) parameterItr.next();
                                if (parameter.getName().contains(parametersFromTextAreaActive[i])) {
                                    List issues = new ArrayList(1);
                                    issues.add(new CustomScanIssue(baseRequestResponse
                                            .getHttpService(), this.helpers
                                            .analyzeRequest(baseRequestResponse)
                                            .getUrl(), new IHttpRequestResponse[0], "Potential RFD Issue Detected", "A parameter named " + parametersFromTextAreaActive[i] + " is detected, this is a potential reflected file download issue, please check this url manually " + this.helpers
                                            .analyzeRequest(baseRequestResponse)
                                            .getUrl()
                                            + "<br><br><b>Issue Definition</b><br><br>"
                                            + "\"Reflected File Download(RFD) is a web attack vector that enables attackers to gain"
                                            + " complete control over a victim ’s machine."
                                            + "In an RFD attack, the user follows a malicious link to a trusted domain resulting in a file download from that domain."
                                            + "computer.\""
                                            + "<br><I>Oren Hafif</I>"
                                            + "<br><br><b>Notes</b><br><br>"
                                            + "\"In the absence of a filename attribute returned within a Content-Disposition "
                                            + "response header, browsers are forced to determine the name of a downloaded file "
                                            + "based on the URL (from the address bar). An attacker can tamper with the \"Path\" "
                                            + "portion of the URL (between the domain name and the question mark sign \"?\") to "
                                            + "set malicious extensions for downloads.\""
                                            + "<br><I>Oren Hafif</I>"
                                            + "<br><br>Sample URL: <br>https://example.com/api;/setup.bat;/setup.bat<br>"
                                            + "<br>Sample HTML code using download attribute:<br>&#x3c;&#x61;&#x20;&#x64;&#x6f;&#x77;&#x6e;&#x6c;&#x6f;&#x61;&#x64;&#x20;&#x68;&#x72;&#x65;&#x66;&#x3d;&#x22;&#x68;&#x74;&#x74;&#x70;&#x73;&#x3a;&#x2f;&#x2f;&#x65;&#x78;&#x61;&#x6d;&#x70;&#x6c;&#x65;&#x2e;&#x63;&#x6f;&#x6d;&#x2f;&#x61;&#x3b;&#x2f;&#x73;&#x65;&#x74;&#x75;&#x70;&#x2e;&#x62;&#x61;&#x74;&#x3b;&#x22;&#x3e;&#x44;&#x6f;&#x77;&#x6e;&#x6c;&#x6f;&#x61;&#x64;&#x20;&#x43;&#x6c;&#x69;&#x65;&#x6e;&#x74;&#x3c;&#x2f;&#x61;&#x3e;<br>"
                                            + "<br>Some useful urls to try from https://www.davidsopas.com/reflected-file-download-cheat-sheet/<br>"
                                            + "https://www.example-site.pt/api/search.bat?term=f00bar&callback=calc<br>"
                                            + "https://www.example-site.pt/api/search;setup.bat?term=f00bar&callback=calc<br>"
                                            + "https://www.example-site.pt/api/search/setup.bat?term=f00bar&callback=calc<br>"
                                            + "https://www.example-site.pt/api/search;/setup.bat?term=f00bar&callback=calc<br>"
                                            + "https://www.example-site.pt/api/search;/setup.bat;?term=f00bar&callback=calc<br>"
                                            + "<br><b>References</b><br><br>"
                                            + "https://www.blackhat.com/docs/eu-14/materials/eu-14-Hafif-Reflected-File-Download-A-New-Web-Attack-Vector.pdf<br>"
                                            + "https://www.davidsopas.com/reflected-file-download-cheat-sheet/<br>"
                                            + "<br><br><b>Development Contact Information</b><br><br>"
                                            + "onur.karasalihoglu@enforsec.com (@onurkarasalih) <br><br>"
                                            + "Special thanks to Oren Hafif (@orenhafif) for the discovery of this vulnerability and support for this plugin", "Medium"));

                                    return issues;
                                }
                            }
                        }
                    }
                }
            }
        }
        return null;

    }

    @Override
    public List<IScanIssue> doActiveScan(IHttpRequestResponse baseRequestResponse, IScannerInsertionPoint insertionPoint) {
        String parametersFromTextAreaActive[] = parametersTextArea.getText().split("\\n");
        String payloadsFromTextAreaActive[] = payloadsTextArea.getText().split("\\n");
        if (this.helpers.analyzeRequest(baseRequestResponse).getMethod().equals("GET")) {
            List responseArray = this.helpers.analyzeResponse(baseRequestResponse.getResponse()).getHeaders();
            Iterator headerItr = responseArray.iterator();
            while (headerItr.hasNext()) {
                String header = headerItr.next().toString();
                if (header.contains("Content-Type:")) {
                    if ((header.contains("json")) || (header.contains("javascript"))) {
                        for (String payloadsFromTextAreaActive1 : payloadsFromTextAreaActive) {
                            // Checking parameter
                            byte[] checkRequest1 = insertionPoint.buildRequest(helpers.stringToBytes(payloadsFromTextAreaActive1));
                            IHttpRequestResponse checkRequestResponse1 = this.callbacks.makeHttpRequest(baseRequestResponse
                                    .getHttpService(), checkRequest1);
                            List matches1 = getMatches(checkRequestResponse1.getResponse(), helpers.stringToBytes(payloadsFromTextAreaActive1));
                            if (matches1.size() > 0) {
                                // Payload reflected in current parameter, if reflected won't continue..
                                List requestHighlights = new ArrayList(1);
                                requestHighlights.add(insertionPoint.getPayloadOffsets(helpers.stringToBytes(payloadsFromTextAreaActive1)));
                                List issues = new ArrayList(1);
                                issues.add(new CustomScanIssue(baseRequestResponse
                                        .getHttpService(), this.helpers
                                        .analyzeRequest(baseRequestResponse)
                                        .getUrl(), new IHttpRequestResponse[]{this.callbacks
                                            .applyMarkers(checkRequestResponse1, requestHighlights, matches1)}, "Reflected File Download", "Submitting "+ this.helpers.bytesToString(helpers.stringToBytes(payloadsFromTextAreaActive1)) +" returned the string:" + this.helpers.bytesToString(helpers.stringToBytes(payloadsFromTextAreaActive1)) + "<br><br>"
                                        + "<b>Issue Definition</b><br><br>"
                                        + "\"Reflected File Download(RFD) is a web attack vector that enables attackers to gain"
                                        + " complete control over a victim ’s machine."
                                        + "In an RFD attack, the user follows a malicious link to a trusted domain resulting in a file download from that domain."
                                        + "computer.\""
                                        + "<br><I>Oren Hafif</I>"
                                        + "<br><br><b>Notes</b><br><br>"
                                        + "\"In the absence of a filename attribute returned within a Content-Disposition "
                                        + "response header, browsers are forced to determine the name of a downloaded file "
                                        + "based on the URL (from the address bar). An attacker can tamper with the \"Path\" "
                                        + "portion of the URL (between the domain name and the question mark sign \"?\") to "
                                        + "set malicious extensions for downloads.\""
                                        + "<br><I>Oren Hafif</I>"
                                        + "<br><br>Sample URL: <br>https://example.com/api;/setup.bat;/setup.bat<br>"
                                        + "<br>Sample HTML code using download attribute:<br>&#x3c;&#x61;&#x20;&#x64;&#x6f;&#x77;&#x6e;&#x6c;&#x6f;&#x61;&#x64;&#x20;&#x68;&#x72;&#x65;&#x66;&#x3d;&#x22;&#x68;&#x74;&#x74;&#x70;&#x73;&#x3a;&#x2f;&#x2f;&#x65;&#x78;&#x61;&#x6d;&#x70;&#x6c;&#x65;&#x2e;&#x63;&#x6f;&#x6d;&#x2f;&#x61;&#x3b;&#x2f;&#x73;&#x65;&#x74;&#x75;&#x70;&#x2e;&#x62;&#x61;&#x74;&#x3b;&#x22;&#x3e;&#x44;&#x6f;&#x77;&#x6e;&#x6c;&#x6f;&#x61;&#x64;&#x20;&#x43;&#x6c;&#x69;&#x65;&#x6e;&#x74;&#x3c;&#x2f;&#x61;&#x3e;<br>"
                                        + "<br>Some useful urls to try from https://www.davidsopas.com/reflected-file-download-cheat-sheet/<br>"
                                        + "https://www.example-site.pt/api/search.bat?term=f00bar&callback=calc<br>"
                                        + "https://www.example-site.pt/api/search;setup.bat?term=f00bar&callback=calc<br>"
                                        + "https://www.example-site.pt/api/search/setup.bat?term=f00bar&callback=calc<br>"
                                        + "https://www.example-site.pt/api/search;/setup.bat?term=f00bar&callback=calc<br>"
                                        + "https://www.example-site.pt/api/search;/setup.bat;?term=f00bar&callback=calc<br>"
                                        + "<br> <b>References</b><br><br>"
                                        + "https://www.blackhat.com/docs/eu-14/materials/eu-14-Hafif-Reflected-File-Download-A-New-Web-Attack-Vector.pdf<br>"
                                        + "https://www.davidsopas.com/reflected-file-download-cheat-sheet/<br>"
                                        + "<br><br><b>Development Contact Information</b><br><br>"
                                        + "onur.karasalihoglu@enforsec.com (@onurkarasalih) <br><br>"
                                        + "Special thanks to Oren Hafif (@orenhafif) for the discovery of this vulnerability and support for this plugin", "High"));
                                return issues;
                            }
                            for (int i = 0; i < parametersFromTextAreaActive.length; i = i + 1) {
                                if (isDebugging) {
                                    mStdOut.println("Checking for parameter " + parametersFromTextAreaActive[i]);
                                }
                                byte[] checkRequest = insertionPoint.buildRequest(helpers.stringToBytes(payloadsFromTextAreaActive1));
                                IHttpRequestResponse checkRequestResponse = this.callbacks.makeHttpRequest(baseRequestResponse
                                        .getHttpService(), checkRequest);
                                List parameters = this.helpers.analyzeRequest(baseRequestResponse).getParameters();
                                Boolean isMatched = false;
                                for (int z = 0; z < parameters.size(); z++) {
                                    if (isDebugging) {
                                        mStdOut.println("Parameter in HTTP request " + ((IParameter) parameters.get(z)).getName());
                                    }
                                    if (((IParameter) parameters.get(z)).getName().equals(parametersFromTextAreaActive[i])) {
                                        // Parameter provided from burp matched in HTTP request
                                        if (isDebugging) {
                                            mStdOut.println("matched! " + parametersFromTextAreaActive[i]);
                                        }
                                        isMatched = true;
                                        List matches = getMatches(checkRequestResponse.getResponse(), helpers.stringToBytes(payloadsFromTextAreaActive1));
                                        if (matches.size() > 0) {
                                            // Payload matched
                                            List requestHighlights = new ArrayList(1);
                                            requestHighlights.add(insertionPoint.getPayloadOffsets(helpers.stringToBytes(payloadsFromTextAreaActive1)));
                                            List issues = new ArrayList(1);
                                            issues.add(new CustomScanIssue(baseRequestResponse
                                                    .getHttpService(), this.helpers
                                                    .analyzeRequest(baseRequestResponse)
                                                    .getUrl(), new IHttpRequestResponse[]{this.callbacks
                                                        .applyMarkers(checkRequestResponse, requestHighlights, matches)}, "Reflected File Download", "Submitting \"||calc|| returned the string:" + this.helpers.bytesToString(helpers.stringToBytes(payloadsFromTextAreaActive1)) + " for " + parametersFromTextAreaActive[i] + " parameter<br><br>"
                                                    + "<b>Issue Definition</b><br><br>"
                                                    + "\"Reflected File Download(RFD) is a web attack vector that enables attackers to gain"
                                                    + " complete control over a victim ’s machine."
                                                    + "In an RFD attack, the user follows a malicious link to a trusted domain resulting in a file download from that domain."
                                                    + "computer.\""
                                                    + "<br><I>Oren Hafif</I>"
                                                    + "<br><br><b>Notes</b><br><br>"
                                                    + "\"In the absence of a filename attribute returned within a Content-Disposition "
                                                    + "response header, browsers are forced to determine the name of a downloaded file "
                                                    + "based on the URL (from the address bar). An attacker can tamper with the \"Path\" "
                                                    + "portion of the URL (between the domain name and the question mark sign \"?\") to "
                                                    + "set malicious extensions for downloads.\""
                                                    + "<br><I>Oren Hafif</I>"
                                                    + "<br><br>Sample URL: <br>https://example.com/api;/setup.bat;/setup.bat<br>"
                                                    + "<br>Sample HTML code using download attribute:<br>&#x3c;&#x61;&#x20;&#x64;&#x6f;&#x77;&#x6e;&#x6c;&#x6f;&#x61;&#x64;&#x20;&#x68;&#x72;&#x65;&#x66;&#x3d;&#x22;&#x68;&#x74;&#x74;&#x70;&#x73;&#x3a;&#x2f;&#x2f;&#x65;&#x78;&#x61;&#x6d;&#x70;&#x6c;&#x65;&#x2e;&#x63;&#x6f;&#x6d;&#x2f;&#x61;&#x3b;&#x2f;&#x73;&#x65;&#x74;&#x75;&#x70;&#x2e;&#x62;&#x61;&#x74;&#x3b;&#x22;&#x3e;&#x44;&#x6f;&#x77;&#x6e;&#x6c;&#x6f;&#x61;&#x64;&#x20;&#x43;&#x6c;&#x69;&#x65;&#x6e;&#x74;&#x3c;&#x2f;&#x61;&#x3e;<br>"
                                                    + "<br>Some useful urls to try from https://www.davidsopas.com/reflected-file-download-cheat-sheet/<br>"
                                                    + "https://www.example-site.pt/api/search.bat?term=f00bar&callback=calc<br>"
                                                    + "https://www.example-site.pt/api/search;setup.bat?term=f00bar&callback=calc<br>"
                                                    + "https://www.example-site.pt/api/search/setup.bat?term=f00bar&callback=calc<br>"
                                                    + "https://www.example-site.pt/api/search;/setup.bat?term=f00bar&callback=calc<br>"
                                                    + "https://www.example-site.pt/api/search;/setup.bat;?term=f00bar&callback=calc<br>"
                                                    + "<br> <b>References</b><br><br>"
                                                    + "https://www.blackhat.com/docs/eu-14/materials/eu-14-Hafif-Reflected-File-Download-A-New-Web-Attack-Vector.pdf<br>"
                                                    + "https://www.davidsopas.com/reflected-file-download-cheat-sheet/<br>"
                                                    + "<br><br><b>Development Contact Information</b><br><br>"
                                                    + "onur.karasalihoglu@enforsec.com (@onurkarasalih) <br><br>"
                                                    + "Special thanks to Oren Hafif (@orenhafif) for the discovery of this vulnerability and support for this plugin", "High"));
                                            return issues;
                                        }
                                    }
                                }
                                if (!isMatched) {
                                    // Parameter from plugin GUI cannot be found in HTTP Request 
                                    if (isDebugging) {
                                        mStdOut.println("Parameter didn't macthed, adding " + parametersFromTextAreaActive[i]);
                                    }
                                    // Adding parameter
                                    IParameter parameter = this.helpers.buildParameter(parametersFromTextAreaActive[i], this.helpers.bytesToString(helpers.stringToBytes(payloadsFromTextAreaActive1)), (byte) 0);
                                    byte[] newRequest = baseRequestResponse.getRequest();
                                    newRequest = this.helpers.addParameter(newRequest, parameter);
                                    // Parameter added to request
                                    // Making HTTP request
                                    IHttpRequestResponse checkRequestResponseAdd = this.callbacks.makeHttpRequest(baseRequestResponse
                                            .getHttpService(), newRequest);
                                    // Get matches
                                    List matches = getMatches(checkRequestResponseAdd.getResponse(), helpers.stringToBytes(payloadsFromTextAreaActive1));
                                    if (matches.size() > 0) {
                                        // response found
                                        List requestHighlights = new ArrayList(1);
                                        // adding highlights
                                        requestHighlights.add(insertionPoint.getPayloadOffsets(helpers.stringToBytes(payloadsFromTextAreaActive1)));
                                        List issues = new ArrayList(1);
                                        issues.add(new CustomScanIssue(baseRequestResponse
                                                .getHttpService(), this.helpers
                                                .analyzeRequest(baseRequestResponse)
                                                .getUrl(), new IHttpRequestResponse[]{this.callbacks
                                                    .applyMarkers(checkRequestResponseAdd, requestHighlights, matches)}, "Reflected File Download", "Submitting " + payloadsFromTextAreaActive1 + " returned the string by adding " + parametersFromTextAreaActive[i] + " parameter: " + payloadsFromTextAreaActive1
                                                + "<br><br><b>Issue Definition</b><br><br>"
                                                + "\"Reflected File Download(RFD) is a web attack vector that enables attackers to gain"
                                                + " complete control over a victim ’s machine."
                                                + "In an RFD attack, the user follows a malicious link to a trusted domain resulting in a file download from that domain."
                                                + "computer.\""
                                                + "<br><I>Oren Hafif</I>"
                                                + "<br><br><b>Notes</b><br><br>"
                                                + "\"In the absence of a filename attribute returned within a Content-Disposition "
                                                + "response header, browsers are forced to determine the name of a downloaded file "
                                                + "based on the URL (from the address bar). An attacker can tamper with the \"Path\" "
                                                + "portion of the URL (between the domain name and the question mark sign \"?\") to "
                                                + "set malicious extensions for downloads.\""
                                                + "<br><I>Oren Hafif</I>"
                                                + "<br><br>Sample URL: <br>https://example.com/api;/setup.bat;/setup.bat<br>"
                                                + "<br>Sample HTML code using download attribute:<br>&#x3c;&#x61;&#x20;&#x64;&#x6f;&#x77;&#x6e;&#x6c;&#x6f;&#x61;&#x64;&#x20;&#x68;&#x72;&#x65;&#x66;&#x3d;&#x22;&#x68;&#x74;&#x74;&#x70;&#x73;&#x3a;&#x2f;&#x2f;&#x65;&#x78;&#x61;&#x6d;&#x70;&#x6c;&#x65;&#x2e;&#x63;&#x6f;&#x6d;&#x2f;&#x61;&#x3b;&#x2f;&#x73;&#x65;&#x74;&#x75;&#x70;&#x2e;&#x62;&#x61;&#x74;&#x3b;&#x22;&#x3e;&#x44;&#x6f;&#x77;&#x6e;&#x6c;&#x6f;&#x61;&#x64;&#x20;&#x43;&#x6c;&#x69;&#x65;&#x6e;&#x74;&#x3c;&#x2f;&#x61;&#x3e;<br>"
                                                + "<br>Some useful urls to try from https://www.davidsopas.com/reflected-file-download-cheat-sheet/<br>"
                                                + "https://www.example-site.pt/api/search.bat?term=f00bar&callback=calc<br>"
                                                + "https://www.example-site.pt/api/search;setup.bat?term=f00bar&callback=calc<br>"
                                                + "https://www.example-site.pt/api/search/setup.bat?term=f00bar&callback=calc<br>"
                                                + "https://www.example-site.pt/api/search;/setup.bat?term=f00bar&callback=calc<br>"
                                                + "https://www.example-site.pt/api/search;/setup.bat;?term=f00bar&callback=calc<br>"
                                                + "<br><b>References</b><br><br>"
                                                + "https://www.blackhat.com/docs/eu-14/materials/eu-14-Hafif-Reflected-File-Download-A-New-Web-Attack-Vector.pdf<br>"
                                                + "https://www.davidsopas.com/reflected-file-download-cheat-sheet/<br>"
                                                + "<br><br><b>Development Contact Information</b><br><br>"
                                                + "onur.karasalihoglu@enforsec.com (@onurkarasalih) <br><br>"
                                                + "Special thanks to Oren Hafif (@orenhafif) for the discovery of this vulnerability and support for this plugin", "High"));
                                        // Adding issue
                                        return issues;
                                    }
                                }
                            }
                        }
                    }
                }
            }

        }
        return null;
    }

    @Override
    public int consolidateDuplicateIssues(IScanIssue existingIssue, IScanIssue newIssue) {
        // This method is called when multiple issues are reported for the same URL 
        // path by the same extension-provided check. The value we return from this 
        // method determines how/whether Burp consolidates the multiple issues
        // to prevent duplication
        //
        // Since the issue name is sufficient to identify our issues as different,
        // if both issues have the same name, only report the existing issue
        // otherwise report both issues
        if (existingIssue.getIssueName().equals(newIssue.getIssueName())) {
            return -1;
        } else {
            return 0;
        }
    }
}

//
// class implementing IScanIssue to hold our custom scan issue details
//
class CustomScanIssue implements IScanIssue {

    private IHttpService httpService;
    private URL url;
    private IHttpRequestResponse[] httpMessages;
    private String name;
    private String detail;
    private String severity;

    public CustomScanIssue(
            IHttpService httpService,
            URL url,
            IHttpRequestResponse[] httpMessages,
            String name,
            String detail,
            String severity) {
        this.httpService = httpService;
        this.url = url;
        this.httpMessages = httpMessages;
        this.name = name;
        this.detail = detail;
        this.severity = severity;
    }

    @Override
    public URL getUrl() {
        return url;
    }

    @Override
    public String getIssueName() {
        return name;
    }

    @Override
    public int getIssueType() {
        return 0;
    }

    @Override
    public String getSeverity() {
        return severity;
    }

    @Override
    public String getConfidence() {
        return "Certain";
    }

    @Override
    public String getIssueBackground() {
        return null;
    }

    @Override
    public String getRemediationBackground() {
        return null;
    }

    @Override
    public String getIssueDetail() {
        return detail;
    }

    @Override
    public String getRemediationDetail() {
        return null;
    }

    @Override
    public IHttpRequestResponse[] getHttpMessages() {
        return httpMessages;
    }

    @Override
    public IHttpService getHttpService() {
        return httpService;
    }

}
