package edu.uhmanoa.jobsearch.UI;

import java.util.ArrayList;
import java.util.LinkedHashMap;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AbsListView;
import android.widget.AbsListView.OnScrollListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import edu.uhmanoa.jobsearch.R;
import edu.uhmanoa.jobsearch.CustomComponents.Job;
import edu.uhmanoa.jobsearch.CustomComponents.JobAdapter;
import edu.uhmanoa.jobsearch.CustomDialogs.FullDescriptionDialog;
import edu.uhmanoa.jobsearch.CustomDialogs.ReLoginDialog;

public class SearchResults extends Activity{
	String mCookie;
	String mSearchResponse;
	String mLoginResponse;
	ListView mListOfJobsListView;
	TextView mNumberOfJobs;
	int mNumberOfJobsFound;
	ArrayList<Job> mListOfJobs;
	JobAdapter mAdapter;
	String mNextLink;
	ProgressDialog pd;
	Job mJobLookingAt;
	LinearLayout mFullDescripHolder;
	Context mContext;
	
	//for the scroll view listener
	int mCurrentVisibleItemCount;
	int mCurrentScrollState;
	int mTotalItemCount;
	int mCurrentFirstVisibleItem;
	int mNumberOfItemsFit;
	//this is the response of the next page of jobs
	String mNextPageResponse;
	
	public static final String DOMAIN_NAME = "https://sece.its.hawaii.edu";
	public static final int GENERAL_ERROR = 1;
	public static final int NO_RESULT_FOUND_ERROR = 2;
	public static final int EXPIRED_COOKIE_ERROR = 3;
	
	//determine what kind of title text for loading dialog
	public static final int GETTING_MORE_JOBS = 4;
	public static final int GETTING_FULL_DESCRIPTION = 5;
	
	@SuppressLint("SetJavaScriptEnabled")
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.search_result);
		
		mListOfJobsListView = (ListView) findViewById(R.id.listOfJobs);
		mNumberOfJobs = (TextView) findViewById(R.id.numberOfResults);
		mListOfJobs = new ArrayList<Job>();
		mFullDescripHolder = (LinearLayout) findViewById(R.id.fullDescriptionWindow);
		mContext = this;
		
		//get the response and the cookie
		Intent thisIntent = this.getIntent();
		mCookie = thisIntent.getStringExtra(Login.COOKIE_VALUE);
		mSearchResponse = thisIntent.getStringExtra(MainStudentMenu.SEARCH_RESPONSE_STRING);
		//need this in case of error
		mLoginResponse = thisIntent.getStringExtra(Login.LOGIN_RESPONSE_STRING);
		
		//parse the header
		Document doc = Jsoup.parse(mSearchResponse);
		Elements header = doc.getElementsByAttributeValue("class", "pagebanner");
		Elements numbers = header.select("font");
			
		if (numbers.isEmpty()) {
			//check if in detailed listing
			if(mSearchResponse.contains("Detailed")) {
				Log.w("SR", "DETAILED LISTING!!!!");
				//show full description
				showFullDescription(mSearchResponse);
			}
			if (mSearchResponse.contains("inactivity")) {
				Log.w("SR", "inactivity" + mSearchResponse);
				showErrorDialog(EXPIRED_COOKIE_ERROR);
			}
			//show error dialog
			if (mSearchResponse.contains("Nothing")) {
				showErrorDialog(NO_RESULT_FOUND_ERROR);
			}
			else {
				showErrorDialog(GENERAL_ERROR);
			}
		}
	
		else {
			//set number of jobs and how many displaying
			mNumberOfJobsFound = Integer.parseInt(numbers.get(0).text());
		
			//check if there was any search keywords
			Elements searchForm = doc.getElementsByAttributeValue("name", "keywords");
			//get the search term
			String keyword = searchForm.attr("value");
			//set the top right text
			if (!keyword.isEmpty()) {
				mNumberOfJobs.setText(mNumberOfJobsFound + " jobs found for \"" + keyword
									 + "\"");
			}
			else {
				mNumberOfJobs.setText(mNumberOfJobsFound + " jobs found"); 
			}
			
			//get the jobs in this initial page view
			getJobs(mSearchResponse);
			//initialize mNextPageResponse
			mNextPageResponse = mSearchResponse;
			//set the adapter
			mAdapter = new JobAdapter(this, R.id.listOfJobs, mListOfJobs);
			mListOfJobsListView.setAdapter(mAdapter);
			
			//set a scroll listener
			mListOfJobsListView.setOnScrollListener(new OnScrollListener() {
				
				@Override
				public void onScrollStateChanged(AbsListView view, int scrollState) {
					mCurrentScrollState = scrollState;
					checkScrollCompleted();
				}
				
				@Override
				public void onScroll(AbsListView view, int firstVisibleItem,
						int visibleItemCount, int totalItemCount) {
						mNumberOfItemsFit = visibleItemCount;
						mCurrentFirstVisibleItem = firstVisibleItem;
						mTotalItemCount = totalItemCount;
					
				}
				public void checkScrollCompleted() {
					if (mCurrentFirstVisibleItem ==(mTotalItemCount - mNumberOfItemsFit)) {
						if (mCurrentScrollState == SCROLL_STATE_IDLE) {
							if (checkNextLink(mNextPageResponse)) {
								if (pd != null && pd.isShowing()) {
									return;
								}
								else {
									showConnectingDialog(GETTING_MORE_JOBS);
									ClickLink getDescription = new ClickLink();
									getDescription.execute(new String[] {mNextLink, "WOOFWOOF"});			
								}
							}
						}
					}
				}
			});
			
			Log.w("SR", "list number:  " + mListOfJobsListView.getCount());
			//listen for click event
			mListOfJobsListView.setOnItemClickListener(new OnItemClickListener() {
	 
				@Override
				public void onItemClick(AdapterView<?> parent, View view, int position,
						long id) {
					mJobLookingAt = (Job) mListOfJobsListView.getItemAtPosition(position);
					launchGetDescription(mJobLookingAt);
				}		
			});			
		}
	}
	public Job createJob(Element job) {
		Elements attributes = job.getElementsByTag("td"); //7 attributes
		//get the job title
		String jobTitle = job.select("a[href]").get(0).text();
		//get the full description link
		String jobFullDescripLink = DOMAIN_NAME + job.select("a[href]").get(0).attr("href");
		String[] split = jobFullDescripLink.split("=");
		String getjobID = split[1].replaceAll("[^0-9]", "");
		String getPayID = split[2].replaceAll("[^0-9]", "");
		int jobID = Integer.parseInt(getjobID);
		int payID = Integer.parseInt(getPayID);
		
		//get the job description preview
		job.select("a[href]").remove(); //remove the links
		String jobDescrip = attributes.get(0).text();
		jobDescrip = jobDescrip.replace("[]","");

		//get the rest of the attributes
		String jobProgram = attributes.get(1).text();
		String jobPay = attributes.get(2).text();
		String jobCategory = attributes.get(3).text();
		String jobLocation = attributes.get(4).text();
		String jobRefNumber = attributes.get(5).text();
		String jobSkillMatch = attributes.get(6).text();
		return new Job(jobTitle, jobDescrip, jobProgram, jobPay, jobCategory, 
				jobLocation, jobRefNumber, jobSkillMatch, jobFullDescripLink, payID, jobID); 
	}
	
	public void getJobs(String response) {
		//get all of the jobs in this page view
		Document doc = Jsoup.parse(response);
		Elements body = doc.getElementsByTag("tbody");
		Element listOfJobs = body.get(3);
		Elements groupOfJobs = listOfJobs.children(); //25 jobs

		for (Element job: groupOfJobs) {
			Job newJob = createJob(job);
			mListOfJobs.add(newJob);
		}
		if (mAdapter != null) {
			mAdapter.notifyDataSetChanged();
		}
	}
	public void launchGetDescription(Job job) {
		showConnectingDialog(GETTING_FULL_DESCRIPTION);
		ClickLink getDescription = new ClickLink();
		getDescription.execute(new String[] {job.mFullDescripLink});
	}
	private class ClickLink extends AsyncTask <String, Void, String>{
		@Override
		protected String doInBackground(String... html) {
				Document doc = null;
				try {
					doc = Jsoup.connect(html[0])
							   .timeout(5000)
							   .cookie(Login.COOKIE_TYPE, mCookie)
							   .get();
					mSearchResponse = doc.toString();
					if (html.length == 2) {
						mSearchResponse = mSearchResponse + "WOOFWOOF";
					}
					return mSearchResponse;
					/*//Log.w("MSTD", "response:  " + doc.text());*/
				} catch (Exception e) { 
					Log.e("SR", "EXCEPTION!!!!");
					Log.e("MSM", e.getMessage());
					showErrorDialog(GENERAL_ERROR);
				}
			return null;
		}
	    @Override
	    protected void onPostExecute(String response) {
	    	if (response != null) {
	    		//adding jobs to the list
	    		if (mSearchResponse.contains("WOOFWOOF")) {
	    			//update this for scroll listener
	    			mNextPageResponse = response;
	    			getJobs(response);
	    			pd.dismiss();
	    			
	    		}
	    		else {
	    			//getting the full description of a job
	    	    	pd.dismiss();
		    		showFullDescription(response);
	    		}
	    	}
	    }
	}
	
	public void showErrorDialog(int type) {
		AlertDialog.Builder builder=  new AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK)
									      .setTitle(R.string.app_name);
		switch(type) {
			case GENERAL_ERROR:{
				builder.setMessage("An error has occured.  Try again?");
				builder.setPositiveButton("Yes", new OnClickListener() {
					
					@Override
					public void onClick(DialogInterface dialog, int which) {
						launchGetDescription(mJobLookingAt);
					}
				});
				builder.setNegativeButton("No", new OnClickListener() {
					
					@Override
					public void onClick(DialogInterface dialog, int which) {
						return;
					}
				});
				break;
			}
			case NO_RESULT_FOUND_ERROR:{
				builder.setMessage("No job matches that criteria");
				builder.setPositiveButton("Go back to search", new OnClickListener() {
					
					@Override
					public void onClick(DialogInterface dialog, int which) {
						//go back to search
						Intent intent = new Intent(getBaseContext(), MainStudentMenu.class);
						intent.putExtra(Login.COOKIE_VALUE, mCookie);
						intent.putExtra(Login.LOGIN_RESPONSE_STRING, mLoginResponse);
					    startActivity(intent);
					}
				});
				builder.setNegativeButton("Quit", new OnClickListener() {
					
					@Override
					public void onClick(DialogInterface dialog, int which) {
						finish();
					}
				});
			}
			case EXPIRED_COOKIE_ERROR:{
				Toast.makeText(getApplicationContext(), "EXPIRED COOKIE", Toast.LENGTH_SHORT).show();
				builder.setMessage("Session has timed out");
				builder.setPositiveButton("Login again", new OnClickListener() {
					
					@Override
					public void onClick(DialogInterface dialog, int which) {
						ReLoginDialog reLoginDialog = new ReLoginDialog(mContext, ReLoginDialog.SEARCH_RESULT_CLASS, mSearchResponse);
						reLoginDialog.show();
					}
				});
			}
		}

		AlertDialog dialog = builder.create();
		//so dialog doesn't get closed when touched outside of it
		dialog.setCanceledOnTouchOutside(false);
		dialog.show();
	}
	/**Get the details for the full job listing*/
	public LinkedHashMap<String,String> getDetails(String response) {
		//Linked HashMap because still want to preserve order
		LinkedHashMap<String,String> listingDetails = new LinkedHashMap<String,String>();
		//get the details
		Document doc = Jsoup.parse(response);
		Elements rows = doc.getElementsByTag("tr");	
		
		for (Element row: rows) {
			if (row.children().size() == 3) {
				String category = row.getElementsByAttributeValue("width", "20%").text();
				Elements detail = row.getElementsByAttributeValue("width", "80%");
				//transform <br> to new lines 
				detail.select("br").append("\\n");
				String detailString = detail.text().replaceAll("\\\\n", "\n");
				
				//fix inconsistent pay string
				if (category.equals("Pay Rate")) {
					if (!detailString.contains("$")) {
						detailString = "$" +detailString;
					}
					int decimal = detailString.indexOf(".");
					//indexing starts at 0
					if (detailString.length() < decimal + 3) {
						detailString = detailString + "0";
					}
				}
				//get the skill matches and if user has skill
				if (category.equals("Skill Matches")) {
					detailString = "";
			    	//only do this if there is a skill matches table
			    	Elements table = row.select("tbody");
			    	//get elements with static class
			    	Elements skillMatches = table.select("td");
			    	//get the skills and if user has them or not
			    	String skillName = null;
			    	for (int i = 0; i < skillMatches.size(); i++) {
			    		//odd number is skill
			    		if ((i %2) == 0) {
			    			skillName = skillMatches.get(i).text();
			    		}
			    		if ((i % 2) == 1) {
			    			//check what kind of picture it is
			    			String url = skillMatches.get(i).select("img").attr("src");
			    			if (url.contains("on")) {
			    				detailString = detailString + skillName + " [ X ] " + "\n";
			    			}
			    			else {
				    			detailString = detailString + skillName + " [    ]" + "\n";			    				
			    			}
			    		}
			    	}					
				}
				//add it to HashMap
				listingDetails.put(category, detailString);
			}
		}
		return listingDetails;
	}
	public void showFullDescription(String response) {
		if (response.contains("inactivity")) {
			Log.w("SR", "inactivity (showFull)");
			showErrorDialog(EXPIRED_COOKIE_ERROR);
		}
		else {
			Dialog fullDescription = new FullDescriptionDialog(this,
					getDetails(response),mJobLookingAt.mJobID, mJobLookingAt.mPayID, 
							   mCookie, mLoginResponse, mSearchResponse);
			fullDescription.show();
		}

	} 
	public boolean checkNextLink(String response) {
		Document doc = Jsoup.parse(response);
		String currentPage = doc.getElementsByTag("strong").get(1).text();
		System.out.println(currentPage);
		//get page links
		Elements pageLinks = doc.getElementsByAttributeValue("class", "pagelinks");
		Elements links = pageLinks.select("a[href]");
		int next = Integer.parseInt(currentPage) + 1;
		boolean matchValue = false;
		
		for (Element link: links) {
			String linkNumber = link.text();
			try {
				if (Integer.parseInt(linkNumber) == next) {
					matchValue = true;
					//get the link
					mNextLink = DOMAIN_NAME + link.getElementsByAttribute("href")
											  .get(0).attr("href");
					break;
				}
			}
			catch(Exception exception){
				continue;
			}
			
		}
		return matchValue;
	}
	public void showConnectingDialog(int type) {
		pd = new ProgressDialog(this, ProgressDialog.THEME_DEVICE_DEFAULT_DARK);
		if (type == GETTING_FULL_DESCRIPTION) {
	        pd.setTitle("Connecting...");
		}
		else {
			pd.setTitle("There's more!");
		}
        //make this a random fact later.  haha.
        pd.setMessage("Please wait.");
        pd.setIndeterminate(true);
        pd.show();
	}
}
