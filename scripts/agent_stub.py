# -*- coding: utf-8 -*-
"""Java agent-service 联调桩：实现 Java→Python 协议的两个端点。

协议（真实 Python Agent 按此实现即可无缝替换）：
  POST /chat/stream  {conversationId, userId, message, history, deepThinking, model}
      -> text/event-stream，事件 token{content} / thinking{content} / done{messageId} / error{code,message}
  POST /chat/stop    {conversationId} -> {"ok": true}
无鉴权，仅内网直连。

运行: pip install fastapi uvicorn && python scripts/agent_stub.py   (监听 0.0.0.0:8001)

测试分支：
  message 以 "error" 开头 -> 触发 error 事件
  message 以 "long"  开头 -> 输出约 40 秒长流（验证网关/agent-service 的 SSE 超时配置）
"""
import asyncio
import json
import uuid

from fastapi import FastAPI
from fastapi.responses import StreamingResponse

app = FastAPI(title="agent-stub")


@app.post("/chat/stream")
async def chat_stream(body: dict):
    conv_id = body.get("conversationId")
    message = body.get("message", "")
    deep = bool(body.get("deepThinking"))
    history = body.get("history", [])

    def ev(name, data):
        return f"event: {name}\ndata: {json.dumps(data, ensure_ascii=False)}\n\n"

    async def gen():
        # 深度思考先出 thinking 事件
        if deep:
            yield ev("thinking", {"content": "让我思考一下这个问题……"})
            await asyncio.sleep(0.5)
            yield ev("thinking", {"content": "思路整理完成，开始回答。"})
            await asyncio.sleep(0.3)

        if message.startswith("error"):
            yield ev("error", {"code": 500, "message": "stub 主动报错"})
            return

        if message.startswith("long"):
            # 长流：约 12 字 * 4 秒 * 3 轮 ≈ 40 秒，用于验证超时配置
            chunks = list("长流测试，每个字间隔四秒。")
            loops = 3
        else:
            chunks = list(f"你好，我是 AI 助手。这是来自 Python 桩的回复。（会话 {conv_id}，历史 {len(history)} 条）")
            loops = 1

        for _ in range(loops):
            for ch in chunks:
                yield ev("token", {"content": ch})
                await asyncio.sleep(4 if loops > 1 else 0.15)

        # Java 侧会忽略此 messageId，用自己生成的 id 落库
        yield ev("done", {"messageId": f"py-{uuid.uuid4().hex}"})

    return StreamingResponse(gen(), media_type="text/event-stream")


@app.post("/chat/stop")
async def chat_stop(body: dict):
    print(f"[stub] stop requested: {body}", flush=True)
    return {"ok": True, "conversationId": body.get("conversationId")}


if __name__ == "__main__":
    import uvicorn

    uvicorn.run(app, host="0.0.0.0", port=8001)
