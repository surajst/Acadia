const http = require('http');

const data = JSON.stringify({
    username: 'teacher_1',
    password: 'PilotLaunchSecure2026!'
});

const loginReq = http.request({
    hostname: 'localhost',
    port: 8080,
    path: '/api/auth/login',
    method: 'POST',
    headers: {
        'Content-Type': 'application/json',
        'Content-Length': data.length
    }
}, (res) => {
    let rawData = '';
    res.on('data', chunk => rawData += chunk);
    res.on('end', () => {
        const json = JSON.parse(rawData);
        const token = json.token;
        console.log("Token:", token);
        
        const searchReq = http.request({
            hostname: 'localhost',
            port: 8080,
            path: '/api/teacher/my-students?q=ar',
            method: 'GET',
            headers: {
                'Authorization': `Bearer ${token}`
            }
        }, (searchRes) => {
            let searchData = '';
            searchRes.on('data', chunk => searchData += chunk);
            searchRes.on('end', () => {
                console.log("Search Result:", searchData);
            });
        });
        searchReq.end();
    });
});

loginReq.write(data);
loginReq.end();
