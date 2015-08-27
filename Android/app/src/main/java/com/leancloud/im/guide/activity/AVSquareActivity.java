package com.leancloud.im.guide.activity;

import android.os.Bundle;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuItem;

import com.avos.avoscloud.im.v2.AVIMClient;
import com.avos.avoscloud.im.v2.AVIMConversation;
import com.avos.avoscloud.im.v2.AVIMConversationQuery;
import com.avos.avoscloud.im.v2.AVIMException;
import com.avos.avoscloud.im.v2.AVIMMessage;
import com.avos.avoscloud.im.v2.callback.AVIMConversationCallback;
import com.avos.avoscloud.im.v2.callback.AVIMConversationQueryCallback;
import com.avos.avoscloud.im.v2.callback.AVIMMessagesQueryCallback;
import com.avos.avoscloud.im.v2.messages.AVIMTextMessage;
import com.leancloud.im.guide.AVImClientManager;
import com.leancloud.im.guide.AVInputBottomBar;
import com.leancloud.im.guide.Constants;
import com.leancloud.im.guide.adapter.MultipleItemAdapter;
import com.leancloud.im.guide.R;
import com.leancloud.im.guide.event.ImTypeMessageEvent;
import com.leancloud.im.guide.event.ImTypeMessageResendEvent;
import com.leancloud.im.guide.event.InputBottomBarTextEvent;

import java.util.Arrays;
import java.util.List;

/**
 * Created by wli on 15/8/13.
 * 流程：1、根据 clientId 获得 AVIMClient 实例
 * 2、根据 conversationId 获得 AVIMConversation 实例
 * 3、必须要加入 conversation 后才能拉取消息
 */
public class AVSquareActivity extends AVEventBaseActivity {

  private AVIMConversation squareConversation;
  private MultipleItemAdapter itemAdapter;
  private RecyclerView recyclerView;
  private LinearLayoutManager layoutManager;
  private SwipeRefreshLayout refreshLayout;
  private AVInputBottomBar inputBottomBar;
  private Toolbar toolbar;

  private static long lastBackTime = 0;
  private final int BACK_INTERVAL = 1000;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_square);

    toolbar = (Toolbar) findViewById(R.id.toolbar);

    recyclerView = (RecyclerView) findViewById(R.id.activity_square_rv_chat);
    refreshLayout = (SwipeRefreshLayout) findViewById(R.id.activity_square_rv_srl_pullrefresh);
    inputBottomBar = (AVInputBottomBar) findViewById(R.id.chat_layout_inputbottombar);
    inputBottomBar.setTag(Constants.SQUARE_CONVERSATION_ID);

    setSupportActionBar(toolbar);

    toolbar.setOnMenuItemClickListener(new Toolbar.OnMenuItemClickListener() {
      @Override
      public boolean onMenuItemClick(MenuItem item) {
        if (R.id.menu_square_members == item.getItemId()) {
          startActivity(AVSquareMembersActivity.class);
          return true;
        }
        return false;
      }
    });

    refreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
      @Override
      public void onRefresh() {
        AVIMMessage message = itemAdapter.getFirstMessage();
        squareConversation.queryMessages(message.getMessageId(), message.getTimestamp(), 20, new AVIMMessagesQueryCallback() {
          @Override
          public void done(List<AVIMMessage> list, AVIMException e) {
            refreshLayout.setRefreshing(false);
            if (null != list && list.size() > 0) {
              itemAdapter.addMessageList(list);
              itemAdapter.notifyDataSetChanged();

              layoutManager.scrollToPositionWithOffset(list.size() - 1, 0);
            }
          }
        });
      }
    });

    layoutManager = new LinearLayoutManager(this);
    recyclerView.setLayoutManager(layoutManager);

    itemAdapter = new MultipleItemAdapter(this);

    getSquare(Constants.SQUARE_CONVERSATION_ID);
    queryInSquare();
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    getMenuInflater().inflate(R.menu.menu_square, menu);
    return true;
  }

  private void getSquare(String conversationId) {
    if (TextUtils.isEmpty(conversationId)) {
      throw new IllegalArgumentException("conversationId can not be null");
    }

    AVIMClient client = AVImClientManager.getInstance().getClient();
    squareConversation = client.getConversation(conversationId);
  }

  private void joinSquare() {
    squareConversation.join(new AVIMConversationCallback() {
      @Override
      public void done(AVIMException e) {
        if (filterException(e)) {
          fetchMessages();
        }
      }
    });
  }

  @Override
  public void onBackPressed() {
    long currentTime = System.currentTimeMillis();
    if (currentTime - lastBackTime < BACK_INTERVAL) {
      super.onBackPressed();
    } else {
      showToast("双击 back 退出");
    }
    lastBackTime = currentTime;
  }

  private void queryInSquare() {
    final AVIMClient client = AVImClientManager.getInstance().getClient();
    AVIMConversationQuery conversationQuery = client.getQuery();
    conversationQuery.whereEqualTo("objectId", Constants.SQUARE_CONVERSATION_ID);
    conversationQuery.containsMembers(Arrays.asList(AVImClientManager.getInstance().getClientId()));
    conversationQuery.findInBackground(new AVIMConversationQueryCallback() {
      @Override
      public void done(List<AVIMConversation> list, AVIMException e) {
        if (null != list && list.size() > 0) {
          fetchMessages();
        } else {
          joinSquare();
        }
      }
    });
  }

  /**
   * 拉取消息，必须加入 conversation 后才能拉取消息
   */
  private void fetchMessages() {
    squareConversation.queryMessages(new AVIMMessagesQueryCallback() {
      @Override
      public void done(List<AVIMMessage> list, AVIMException e) {
        if (filterException(e)) {
          itemAdapter.setMessageList(list);
          recyclerView.setAdapter(itemAdapter);
          itemAdapter.notifyDataSetChanged();
          scrollToBottom();
        }
      }
    });
  }

  public void onEvent(InputBottomBarTextEvent textEvent) {
    if (!TextUtils.isEmpty(textEvent.sendContent) && Constants.SQUARE_CONVERSATION_ID.equals(textEvent.tag)) {
      AVIMTextMessage message = new AVIMTextMessage();
      message.setText(textEvent.sendContent);
      itemAdapter.addMessage(message);
      itemAdapter.notifyDataSetChanged();
      scrollToBottom();
      squareConversation.sendMessage(message, new AVIMConversationCallback() {
        @Override
        public void done(AVIMException e) {
          itemAdapter.notifyDataSetChanged();
        }
      });
    }
  }

  public void onEvent(ImTypeMessageEvent event) {
    if (null != squareConversation && null != event &&
      squareConversation.getConversationId().equals(event.conversation.getConversationId())) {
      itemAdapter.addMessage(event.message);
      itemAdapter.notifyDataSetChanged();
      scrollToBottom();
    }
  }

  public void onEvent(ImTypeMessageResendEvent event) {
    if (AVIMMessage.AVIMMessageStatus.AVIMMessageStatusFailed == event.message.getMessageStatus()
      && squareConversation.getConversationId().equals(event.message.getConversationId())) {
      squareConversation.sendMessage(event.message, new AVIMConversationCallback() {
        @Override
        public void done(AVIMException e) {
          itemAdapter.notifyDataSetChanged();
        }
      });
      itemAdapter.notifyDataSetChanged();
    }
  }

  private void scrollToBottom() {
    layoutManager.scrollToPositionWithOffset(itemAdapter.getItemCount() - 1, 0);
  }
}
