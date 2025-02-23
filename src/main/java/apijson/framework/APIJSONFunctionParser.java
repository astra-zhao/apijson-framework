/*Copyright ©2016 TommyLemon(https://github.com/TommyLemon/APIJSON)

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.*/

package apijson.framework;

import static apijson.framework.APIJSONConstant.FUNCTION_;

import java.io.IOException;
import java.rmi.ServerException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.servlet.http.HttpSession;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

import apijson.JSON;
import apijson.JSONResponse;
import apijson.Log;
import apijson.NotNull;
import apijson.RequestMethod;
import apijson.orm.AbstractVerifier;
import apijson.StringUtil;
import apijson.orm.AbstractFunctionParser;
import apijson.orm.JSONRequest;
import unitauto.MethodUtil;
import unitauto.MethodUtil.Argument;


/**可远程调用的函数类
 * @author Lemon
 */
public class APIJSONFunctionParser extends AbstractFunctionParser {
	public static final String TAG = "APIJSONFunctionParser";

	@NotNull
	public static APIJSONCreator APIJSON_CREATOR;
	static {
		APIJSON_CREATOR = new APIJSONCreator();
	}

	private HttpSession session;
	public APIJSONFunctionParser() {
		this(null);
	}
	public APIJSONFunctionParser(HttpSession session) {
		this(null, null, 0, null, session);
	}
	public APIJSONFunctionParser(RequestMethod method, String tag, int version, JSONObject request, HttpSession session) {
		super(method, tag, version, request);
		setSession(session);
	}
	public HttpSession getSession() {
		return session;
	}
	public APIJSONFunctionParser setSession(HttpSession session) {
		this.session = session;
		return this;
	}

	@Override
	public APIJSONFunctionParser setMethod(RequestMethod method) {
		super.setMethod(method);
		return this;
	}
	@Override
	public APIJSONFunctionParser setTag(String tag) {
		super.setTag(tag);
		return this;
	}
	@Override
	public APIJSONFunctionParser setVersion(int version) {
		super.setVersion(version);
		return this;
	}

	/**初始化，加载所有远程函数配置，并校验是否已在应用层代码实现
	 * @return 
	 * @throws ServerException
	 */
	public static JSONObject init() throws ServerException {
		return init(false);
	}
	/**初始化，加载所有远程函数配置，并校验是否已在应用层代码实现
	 * @param shutdownWhenServerError 
	 * @return 
	 * @throws ServerException
	 */
	public static JSONObject init(boolean shutdownWhenServerError) throws ServerException {
		return init(shutdownWhenServerError, null);
	}
	/**初始化，加载所有远程函数配置，并校验是否已在应用层代码实现
	 * @param creator 
	 * @return 
	 * @throws ServerException
	 */
	public static JSONObject init(APIJSONCreator creator) throws ServerException {
		return init(false, creator);
	}
	/**初始化，加载所有远程函数配置，并校验是否已在应用层代码实现
	 * @param shutdownWhenServerError 
	 * @param creator 
	 * @return 
	 * @throws ServerException
	 */
	public static JSONObject init(boolean shutdownWhenServerError, APIJSONCreator creator) throws ServerException {
		return init(shutdownWhenServerError, creator, null);
	}
	/**初始化，加载所有远程函数配置，并校验是否已在应用层代码实现
	 * @param shutdownWhenServerError 
	 * @param creator 
	 * @param table 表内自定义数据过滤条件
	 * @return 
	 * @throws ServerException
	 */
	public static JSONObject init(boolean shutdownWhenServerError, APIJSONCreator creator, JSONObject table) throws ServerException {
		if (creator == null) {
			creator = APIJSON_CREATOR;
		}
		APIJSON_CREATOR = creator;


		boolean isAll = table == null || table.isEmpty();
		
		JSONObject function = isAll ? new JSONRequest() : table;
		JSONRequest functionItem = new JSONRequest();
		functionItem.put(FUNCTION_, function);

		JSONObject request = new JSONObject(); 
		request.putAll(functionItem.toArray(0, 0, FUNCTION_));


		JSONObject response = creator.createParser().setMethod(RequestMethod.GET).setNeedVerify(true).parseResponse(request);
		if (JSONResponse.isSuccess(response) == false) {
			onServerError("\n\n\n\n\n !!!! 查询远程函数异常 !!!\n" + response.getString(JSONResponse.KEY_MSG) + "\n\n\n\n\n", shutdownWhenServerError);
		}

		JSONArray list = response.getJSONArray(FUNCTION_ + "[]");
		int size = list == null ? 0 : list.size();
		if (isAll && size <= 0) {
			Log.w(TAG, "init isAll && size <= 0，，没有可用的远程函数");
			throw new NullPointerException("没有可用的远程函数");
		}
		
		if (table == null || table.isEmpty()) {  // 全量更新
			FUNCTION_MAP.clear();
		}

		JSONObject item;
		for (int i = 0; i < size; i++) {
			item = list.getJSONObject(i);
			if (item == null) {
				continue;
			}

			JSONObject demo = JSON.parseObject(item.getString("demo"));
			if (demo == null) {
				onServerError("字段 demo 的值必须为合法且非 null 的 JSONObejct 字符串！", shutdownWhenServerError);
			}
			String name = item.getString("name");
			if (demo.containsKey("result()") == false) {
				demo.put("result()", getFunctionCall(name, item.getString("arguments")));
			}
			//			demo.put(JSONRequest.KEY_TAG, item.getString(JSONRequest.KEY_TAG));
			//			demo.put(JSONRequest.KEY_VERSION, item.getInteger(JSONRequest.KEY_VERSION));

			FUNCTION_MAP.put(name, item);  //必须在测试 invoke 前！

			String[] methods = StringUtil.split(item.getString("methods"));
			JSONObject r = new APIJSONParser(
					methods == null || methods.length <= 0 ? RequestMethod.GET : RequestMethod.valueOf(methods[0])
							, false
					)
					.setTag(item.getString(JSONRequest.KEY_TAG))
					.setVersion(item.getIntValue(JSONRequest.KEY_VERSION))
					.parseResponse(demo);

			if (JSONResponse.isSuccess(r) == false) {
				onServerError(JSONResponse.getMsg(r), shutdownWhenServerError);
			}

		}

		return response;
	}


	private static void onServerError(String msg, boolean shutdown) throws ServerException {
		Log.e(TAG, "\n远程函数文档测试未通过！\n请新增 demo 里的函数，或修改 Function 表里的 demo 为已有的函数示例！\n保证前端看到的远程函数文档是正确的！！！\n\n原因：\n" + msg);

		if (shutdown) {
			System.exit(1);	
		} else {
			throw new ServerException(msg);
		}
	}


	public static void test() throws Exception {
		test(null);
	}
	public static void test(APIJSONFunctionParser function) throws Exception {
		int i0 = 1, i1 = -2;
		JSONObject request = new JSONObject(); 
		request.put("id", 10);
		request.put("i0", i0);
		request.put("i1", i1);
		JSONArray arr = new JSONArray();
		arr.add(new JSONObject());
		request.put("arr", arr);

		JSONArray array = new JSONArray();
		array.add(1);//new JSONObject());
		array.add(2);//new JSONObject());
		array.add(4);//new JSONObject());
		array.add(10);//new JSONObject());
		request.put("array", array);

		request.put("position", 1);
		request.put("@position", 0);

		request.put("key", "key");
		JSONObject object = new JSONObject();
		object.put("key", "success");
		request.put("object", object);

		if (function == null) {
			function = new APIJSONFunctionParser(null, null, 1, null, null);
		}

		// 等数据库 Function 表加上 plus 配置再过两个以上迭代(应该是到 5.0)后再取消注释
//		Log.i(TAG, "plus(1,-2) = " + function.invoke("plus(i0,i1)", request));
//		AssertUtil.assertEqual(-1, function.invoke("plus(i0,i1)", request));
		
		Log.i(TAG, "count([1,2,4,10]) = " + function.invoke("countArray(array)", request));
		AssertUtil.assertEqual(4, function.invoke("countArray(array)", request));
		
		Log.i(TAG, "isContain([1,2,4,10], 10) = " + function.invoke("isContain(array,id)", request));
		AssertUtil.assertEqual(true, function.invoke("isContain(array,id)", request));

		Log.i(TAG, "getFromArray([1,2,4,10], 0) = " + function.invoke("getFromArray(array,@position)", request));
		AssertUtil.assertEqual(1, function.invoke("getFromArray(array,@position)", request));

		Log.i(TAG, "getFromObject({key:\"success\"}, key) = " + function.invoke("getFromObject(object,key)", request));
		AssertUtil.assertEqual("success", function.invoke("getFromObject(object,key)", request));

	}







	/**获取远程函数的demo，如果没有就自动补全
	 * @param request
	 * @return
	 * @throws ServerException 
	 */
	public JSONObject getFunctionDemo(@NotNull JSONObject request) {
		JSONObject demo = JSON.parseObject(request.getString("demo"));
		if (demo == null) {
			demo = new JSONObject();
		}
		if (demo.containsKey("result()") == false) {
			demo.put("result()", getFunctionCall(request.getString("name"), request.getString("arguments")));
		}
		return demo;
	}

	/**获取远程函数的demo，如果没有就自动补全
	 * @param request
	 * @return
	 */
	public String getFunctionDetail(@NotNull JSONObject request) {
		return getFunctionCall(request.getString("name"), request.getString("arguments"))
				+ ": " + StringUtil.getTrimedString(request.getString("detail"));
	}
	/**获取函数调用代码
	 * @param name
	 * @param arguments
	 * @return
	 */
	private static String getFunctionCall(String name, String arguments) {
		return name + "(" + StringUtil.getTrimedString(arguments) + ")";
	}

	/**TODO 仅用来测试 "key-()":"getIdList()" 和 "key()":"getIdList()"
	 * @param request
	 * @return JSONArray 只能用JSONArray，用long[]会在SQLConfig解析崩溃
	 * @throws Exception
	 */
	public JSONArray getIdList(@NotNull JSONObject request) {
		return new JSONArray(new ArrayList<Object>(Arrays.asList(12, 15, 301, 82001, 82002, 38710)));
	}


	/**TODO 仅用来测试 "key-()":"verifyAccess()"
	 * @param request
	 * @return
	 * @throws Exception
	 */
	public Object verifyAccess(@NotNull JSONObject request) throws Exception {
		long userId = request.getLongValue(apijson.JSONObject.KEY_USER_ID);
		String role = request.getString(apijson.JSONObject.KEY_ROLE);
		if (AbstractVerifier.OWNER.equals(role) && userId != APIJSONVerifier.getVisitorId(session)) {
			throw new IllegalAccessException("登录用户与角色OWNER不匹配！");
		}
		return null;
	}




	public double plus(@NotNull JSONObject request, String i0, String i1) {
		return request.getDoubleValue(i0) + request.getDoubleValue(i1);
	}
	public double minus(@NotNull JSONObject request, String i0, String i1) {
		return request.getDoubleValue(i0) - request.getDoubleValue(i1);
	}
	public double multiply(@NotNull JSONObject request, String i0, String i1) {
		return request.getDoubleValue(i0) * request.getDoubleValue(i1);
	}
	public double divide(@NotNull JSONObject request, String i0, String i1) {
		return request.getDoubleValue(i0) / request.getDoubleValue(i1);
	}

	//判断是否为空 <<<<<<<<<<<<<<<<<<<<<<<<<<<<<
	/**判断array是否为空
	 * @param request
	 * @param array
	 * @return
	 */
	public boolean isArrayEmpty(@NotNull JSONObject request, String array) {
		return BaseModel.isEmpty(request.getJSONArray(array));
	}
	/**判断object是否为空
	 * @param request
	 * @param object
	 * @return
	 */
	public boolean isObjectEmpty(@NotNull JSONObject request, String object) {
		return BaseModel.isEmpty(request.getJSONObject(object)); 
	}
	//判断是否为空 >>>>>>>>>>>>>>>>>>>>>>>>>>>>>

	//判断是否为包含 <<<<<<<<<<<<<<<<<<<<<<<<<<<<<
	/**判断array是否包含value
	 * @param request
	 * @param array
	 * @param value
	 * @return
	 */
	public boolean isContain(@NotNull JSONObject request, String array, String value) {
		//解决isContain((List<Long>) [82001,...], (Integer) 82001) == false及类似问题, list元素可能是从数据库查到的bigint类型的值
		//		return BaseModel.isContain(request.getJSONArray(array), request.get(value));

		//不用准确的的 request.getString(value).getClass() ，因为Long值转Integer崩溃，而且转成一种类型本身就和字符串对比效果一样了。
		List<String> list = com.alibaba.fastjson.JSON.parseArray(request.getString(array), String.class);
		return list != null && list.contains(request.getString(value));
	}
	/**判断object是否包含key
	 * @param request
	 * @param object
	 * @param key
	 * @return
	 */
	public boolean isContainKey(@NotNull JSONObject request, String object, String key) { 
		return BaseModel.isContainKey(request.getJSONObject(object), request.getString(key)); 
	}
	/**判断object是否包含value
	 * @param request
	 * @param object
	 * @param value
	 * @return
	 */
	public boolean isContainValue(@NotNull JSONObject request, String object, String value) { 
		return BaseModel.isContainValue(request.getJSONObject(object), request.get(value));
	}
	//判断是否为包含 >>>>>>>>>>>>>>>>>>>>>>>>>>>>>


	//获取集合长度 <<<<<<<<<<<<<<<<<<<<<<<<<<<<<
	/**获取数量
	 * @param request
	 * @param array
	 * @return
	 */
	public int countArray(@NotNull JSONObject request, String array) { 
		return BaseModel.count(request.getJSONArray(array)); 
	}
	/**获取数量
	 * @param request
	 * @param object
	 * @return
	 */
	public int countObject(@NotNull JSONObject request, String object) {
		return BaseModel.count(request.getJSONObject(object)); 
	}
	//获取集合长度 >>>>>>>>>>>>>>>>>>>>>>>>>>>>>


	//根据键获取值 <<<<<<<<<<<<<<<<<<<<<<<<<<<<<
	/**获取
	 ** @param request
	 * @param array
	 * @param position 支持直接传数字，例如 getFromArray(array,0) ；或者引用当前对象的值，例如 "@position": 0, "result()": "getFromArray(array,@position)"
	 * @return
	 */
	public Object getFromArray(@NotNull JSONObject request, String array, String position) {
		int p;
		try {
			p = Integer.parseInt(position);
		} catch (Exception e) {
			p = request.getIntValue(position);
		}
		return BaseModel.get(request.getJSONArray(array), p); 
	}
	/**获取
	 * @param request
	 * @param object
	 * @param key
	 * @return
	 */
	public Object getFromObject(@NotNull JSONObject request, String object, String key) { 
		return BaseModel.get(request.getJSONObject(object), request.getString(key));
	}
	//根据键获取值 >>>>>>>>>>>>>>>>>>>>>>>>>>>>>

	//根据键移除值 <<<<<<<<<<<<<<<<<<<<<<<<<<<<<
	/**移除
	 ** @param request
	 * @param array
	 * @param position 支持直接传数字，例如 getFromArray(array,0) ；或者引用当前对象的值，例如 "@position": 0, "result()": "getFromArray(array,@position)"
	 * @return
	 */
	public Object removeIndex(@NotNull JSONObject request, String position) {
		int p;
		try {
			p = Integer.parseInt(position);
		} catch (Exception e) {
			p = request.getIntValue(position);
		}
		request.remove(p); 
		return null;
	}
	/**移除
	 * @param request
	 * @param object
	 * @param key
	 * @return
	 */
	public Object removeKey(@NotNull JSONObject request, String key) { 
		request.remove(key);
		return null;
	}
	//根据键获取值 >>>>>>>>>>>>>>>>>>>>>>>>>>>>>



	//获取非基本类型对应基本类型的非空值 <<<<<<<<<<<<<<<<<<<<<<<<<<<<<
	/**获取非空值
	 * @param request
	 * @param value
	 * @return
	 */
	public boolean booleanValue(@NotNull JSONObject request, String value) { 
		return request.getBooleanValue(value);
	}
	/**获取非空值
	 * @param request
	 * @param value
	 * @return
	 */
	public int intValue(@NotNull JSONObject request, String value) {  
		return request.getIntValue(value);
	}
	/**获取非空值
	 * @param request
	 * @param value
	 * @return
	 */
	public long longValue(@NotNull JSONObject request, String value) {   
		return request.getLongValue(value);
	}
	/**获取非空值
	 * @param request
	 * @param value
	 * @return
	 */
	public float floatValue(@NotNull JSONObject request, String value) {  
		return request.getFloatValue(value);
	}
	/**获取非空值
	 * @param request
	 * @param value
	 * @return
	 */
	public double doubleValue(@NotNull JSONObject request, String value) {    
		return request.getDoubleValue(value); 
	}
	//获取非基本类型对应基本类型的非空值 >>>>>>>>>>>>>>>>>>>>>>>>>>>>>

	/**获取value，当value为null时获取defaultValue
	 * @param request
	 * @param value
	 * @param defaultValue
	 * @return v == null ? request.get(defaultValue) : v
	 */
	public Object getWithDefault(@NotNull JSONObject request, String value, String defaultValue) {    
		Object v = request.get(value); 
		return v == null ? request.get(defaultValue) : v; 
	}



	/**获取方法参数的定义
	 * @param request
	 * @return
	 * @throws IOException 
	 * @throws ClassNotFoundException 
	 * @throws IllegalArgumentException 
	 */
	public String getMethodArguments(@NotNull JSONObject request) throws IllegalArgumentException, ClassNotFoundException, IOException {
		return getMethodArguments(request, "methodArgs");
	}
	/**获取方法参数的定义
	 * @param request
	 * @param requestKey
	 * @param methodArgs
	 * @return
	 * @throws IllegalArgumentException
	 * @throws ClassNotFoundException
	 * @throws IOException
	 */
	public String getMethodArguments(@NotNull JSONObject request, String methodArgsKey) throws IllegalArgumentException, ClassNotFoundException, IOException {
		JSONObject obj = request.getJSONObject("request");
		String argsStr = obj == null ? null : obj.getString(methodArgsKey);
		if (StringUtil.isEmpty(argsStr, true)) {
			argsStr = request.getString(methodArgsKey);
		}
		List<Argument> methodArgs = JSON.parseArray(removeComment(argsStr), Argument.class);
		if (methodArgs == null || methodArgs.isEmpty()) {
			return "";
		}

		//		Class<?>[] types = new Class<?>[methodArgs.size()];
		//		Object[] args = new Object[methodArgs.size()];
		//		MethodUtil.initTypesAndValues(methodArgs, types, args, true);

		String s = "";
		//		if (types != null) {
		//			String sn;
		//			for (int i = 0; i < types.length; i++) {
		//				sn = types[i] == null ? null : types[i].getSimpleName();
		//				if (sn == null) {
		//					sn = Object.class.getSimpleName();
		//				}
		//
		//				if (i > 0) {
		//					s += ",";
		//				}
		//
		//				if (MethodUtil.CLASS_MAP.containsKey(sn)) {
		//					s += sn;
		//				}
		//				else {
		//					s += types[i].getName();
		//				}
		//			}
		//		}

		for (int i = 0; i < methodArgs.size(); i++) {
			Argument arg = methodArgs.get(i);

			String sn = arg == null ? null : arg.getType();
			if (sn == null) {
				sn = arg.getValue() == null ? Object.class.getSimpleName() : MethodUtil.trimType(arg.getValue().getClass());
			}

			if (i > 0) {
				s += ",";
			}
			s += sn;
		}

		return s;
	}


	/**获取方法的定义
	 * @param request
	 * @return
	 * @throws IOException 
	 * @throws ClassNotFoundException 
	 * @throws IllegalArgumentException 
	 */
	public String getMethodDefination(@NotNull JSONObject request)
			throws IllegalArgumentException, ClassNotFoundException, IOException {
		//		request.put("arguments", removeComment(request.getString("methodArgs")));
		return getMethodDefination(request, "method", "arguments", "genericType", "genericExceptions", "Java");
	}
	/**获取方法的定义
	 * @param request
	 * @param method
	 * @param arguments
	 * @param type
	 * @return method(argType0,argType1...): returnType
	 * @throws IOException 
	 * @throws ClassNotFoundException 
	 * @throws IllegalArgumentException 
	 */
	public String getMethodDefination(@NotNull JSONObject request, String method, String arguments, String type, String exceptions, String language)
			throws IllegalArgumentException, ClassNotFoundException, IOException {
		String n = request.getString(method);
		if (StringUtil.isEmpty(n, true)) {
			throw new NullPointerException("getMethodDefination  StringUtil.isEmpty(methodArgs, true) !");
		}
		String a = request.getString(arguments);
		String t = request.getString(type);
		String e = request.getString(exceptions);

		if (language == null) {
			language = "";
		}
		switch (language) {
		case "TypeScript":
			return n + "(" + (StringUtil.isEmpty(a, true) ? "" : a) + ")" + (StringUtil.isEmpty(t, true) ? "" : ": " + t) + (StringUtil.isEmpty(e, true) ? "" : " throws " + e);
		case "Go":
			return n + "(" + (StringUtil.isEmpty(a, true) ? "" : a ) + ")" + (StringUtil.isEmpty(t, true) ? "" : " " + t) + (StringUtil.isEmpty(e, true) ? "" : " throws " + e);
		default:
			//类型可能很长，Eclipse, Idea 代码提示都是类型放后面			return (StringUtil.isEmpty(t, true) ? "" : t + " ") + n + "(" + (StringUtil.isEmpty(a, true) ? "" : a) + ")";
			return n + "(" + (StringUtil.isEmpty(a, true) ? "" : a) + ")" + (StringUtil.isEmpty(t, true) ? "" : ": " + t) + (StringUtil.isEmpty(e, true) ? "" : " throws " + e);
		}
	}

	/**
	 * methodArgs 和 classArgs 都可以带注释
	 */
	public String getMethodRequest(@NotNull JSONObject request) {
		String req = request.getString("request");
		if (StringUtil.isEmpty(req, true) == false) {
			return req;
		}

		req = "{";
		Boolean isStatic = request.getBoolean("static");
		String methodArgs = request.getString("methodArgs");
		String classArgs = request.getString("classArgs");

		boolean comma = false;
		if (isStatic != null && isStatic) {
			req += "\n    \"static\": " + true;
			comma = true;
		}
		if (StringUtil.isEmpty(methodArgs, true) == false) {
			req += (comma ? "," : "") + "\n    \"methodArgs\": " + methodArgs;
			comma = true;
		} 
		if (StringUtil.isEmpty(classArgs, true) == false) {
			req += (comma ? "," : "") + "\n    \"classArgs\": " + classArgs;
		}
		req += "\n}";
		return req;
	}

	//	public static JSONObject removeComment(String json) {
	//		return JSON.parseObject(removeComment(json));
	//	}
	public static String removeComment(String json) {
		return json == null ? null: json.replaceAll("(//.*)|(/\\*[\\s\\S]*?\\*/)", "");
	}

}