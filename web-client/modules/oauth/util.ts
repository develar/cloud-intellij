export function parseQueryString(queryString: string): { [key: string]: string; } {
  if (queryString == null || queryString.length === 0) {
    return null
  }

  var queryParameterPairRE = /([^&;=]+)=?([^&;]*)/g
  var decode = function (s: string): string {
    return decodeURIComponent(s.replace(/\+/g, ' '))
  }

  var urlParams: { [key: string]: string; } = Object.create(null)
  var matchedQueryPair: string[]
  while ((matchedQueryPair = queryParameterPairRE.exec(queryString)) != null) {
    urlParams[decode(matchedQueryPair[1])] = decode(matchedQueryPair[2])
  }
  return urlParams
}

export function encodeQuery(o: any): string {
  let a = <string[]>[]
  for (let x in o) {
    if (o.hasOwnProperty(x)) {
      var v = o[x]
      if (v != null) {
        a.push(x + "=" + encodeURIComponent(v))
      }
    }
  }
  return a.length === 0 ? null : a.join("&")
}

export function toQs(url: string, params: any): string {
  var query = encodeQuery(params)
  if (query == null) {
    return url
  }
  else {
    return url + "?" + query
  }
}

export function extend(to: any, from: any) {
  if (to instanceof Object && from instanceof Object && to !== from) {
    for (let propertyName in from) {
      //noinspection JSUnfilteredForInLoop
      to[propertyName] = extend(to[propertyName], from[propertyName])
    }
    return to
  }
  else {
    return from
  }
}

function createError(code: string|number, message: string) {
  return {
    error: {
      code: code,
      message: message
    }
  };
}

export function xhr(method: string, url: string, callback: any, headers?: { [key: string]: string; }, data?: any): XMLHttpRequest {
  var r = new XMLHttpRequest()

  if (method === "blob") {
    r.responseType = "blob"
    method = "get"
  }

  r.onload = function () {
    var json = r.response
    try {
      json = JSON.parse(r.responseText)
    }
    catch (_e) {
      if (r.status === 401) {
        json = createError("access_denied", r.statusText);
      }
    }

    var headers = headersToJSON(r.getAllResponseHeaders())
    headers["statusCode"] = r.status

    callback(json || (method === "GET" ? createError('empty_response', 'Could not get resource') : {}), headers)
  }

  r.onerror = function (e) {
    console.error(e)

    var json = r.responseText
    try {
      json = JSON.parse(r.responseText)
    }
    catch (_e) {
      console.error(_e)
    }

    callback(json || createError('access_denied', "Could not get resource"))
  };

  if (data != null && typeof (data) !== 'string' && !(data instanceof FormData) && !(data instanceof File) && !(data instanceof Blob)) {
    var f = new FormData()
    for (let x in data) if (data.hasOwnProperty(x)) {
      if (data[x] instanceof HTMLInputElement) {
        if ('files' in data[x] && data[x].files.length > 0) {
          f.append(x, data[x].files[0]);
        }
      }
      else if (data[x] instanceof Blob) {
        f.append(x, data[x], data.name);
      }
      else {
        f.append(x, data[x]);
      }
    }

    data = f;
  }

  r.open(method, url)

  if (headers != null) {
    for (let name in headers) {
      if (headers.hasOwnProperty(name)) {
        r.setRequestHeader(name, headers[name])
      }
    }
  }

  r.send(data)
  return r
}

// Headers are returned as a string
export function headersToJSON(s: string): { [key: string]: string|number; } {
  var r: { [key: string]: string; } = {}
  var reg = /([a-z\-]+):\s?(.*);?/gi
  var m: string[]
  while ((m = reg.exec(s))) {
    r[m[1]] = m[2];
  }
  return r
}

export function isEmpty(obj: any): boolean {
  if (!obj) {
    return true;
  }
  else if (Array.isArray(obj)) {
    return !obj.length;
  }
  else if (typeof (obj) === 'object') {
    for (var key in obj) {
      if (obj.hasOwnProperty(key)) {
        return false;
      }
    }
  }
  return true;
}

export function iframe(src: any): void {
  this.append("iframe", {
    src: src,
    style: {position: 'absolute', left: '-1000px', bottom: 0, height: '1px', width: '1px'}
  }, "body");
}

export function merge(a: any, b: any) {
  var r = extend({}, a)
  extend(r, b)
  return r
}

export function toUrl(path: string): any {
  var urlCtor: any = URL
  if (path == null || path.length === 0) {
    return window.location
  }
  else if (urlCtor != null && urlCtor.length !== 0) {
    return new urlCtor(path, window.location)
  }
  else {
    var a = document.createElement("a")
    a.href = path
    return a
  }
}

export function computeDiff(a: any[], b: any[]) {
  return b.filter(function (item) {
    return a.indexOf(item) === -1
  });
}

// Unique
// Remove duplicate and null values from an array
// @param a array
export function unique(a: any[]) {
  return a.filter(function (item, index) {
    // Is this the first location of item
    return a.indexOf(item) === index
  })
}