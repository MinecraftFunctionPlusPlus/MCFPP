/** 
 * author: AjaxGb, Sol Toder, XiLaiTL
 * source: https://github.com/AjaxGb/mc-uuid-converter/blob/master/convert.js */
/**
MIT License

Copyright (c) 2020 Sol Toder

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
 */

import { webcrypto } from 'node:crypto'
const UUID_GROUP_SIZES = [8, 4, 4, 4, 12];
const uuid16_reg = /^[0-9a-fA-F]{1,8}-[0-9a-fA-F]{1,4}-[0-9a-fA-F]{1,4}-[0-9a-fA-F]{1,4}-[0-9a-fA-F]{1,12}|[0-9a-fA-F]{1,32}$/;
const integer_reg = /^[-+]?\d+$/;
export class UUID{
    private uuidBytes = new Uint8Array(16);
    private uuid = new DataView(this.uuidBytes.buffer);
    constructor() { }
    
    static random() {
        const random: UUID = new UUID();
        webcrypto.getRandomValues(random.uuidBytes);
        // Set version to 4 (random)
        random.uuidBytes[6] = (random.uuidBytes[6] & 0x0f) | (4 << 4);
        // Set variant to 1 (Leach–Salz)
        random.uuidBytes[8] = (random.uuidBytes[8] & 0x3f) | 0x80;
        return random
    }
    static fromArray(array:string[]) {
        const res: UUID = new UUID();
        for (let i = 0; i < array.length; i++) {
            if (integer_reg.test(array[i])) {
                res.uuid.setInt32(i * 4, Number(array[i]), false);
            }
        }
        return res
    }
    static fromHex([hex]:[string]) {
        const res: UUID = new UUID();
        if (!uuid16_reg.test(hex)) return res;
        hex = hex.trim();
        if (hex.includes('-')) {
            hex = hex
                .split('-')
                .map((str, i) => str.padStart(UUID_GROUP_SIZES[i], '0'))
                .join('');
        } else {
            hex = hex.padStart(32, '0');
        }
        res.uuid.setBigUint64(0, BigInt('0x' + hex.substring(0, 16)), false);
        res.uuid.setBigUint64(8, BigInt('0x' + hex.substring(16)), false);
        return res
    }
    static fromMostLeast([most, least]:[string,string]) {
        const res: UUID = new UUID()
        if (integer_reg.test(most)) {
            res.uuid.setBigInt64(0, BigInt(most), false);}
        if (integer_reg.test(least)) {
            res.uuid.setBigInt64(8, BigInt(least), false);}
        return res
    }
    toArray() {
        const array: string[] = [];
        for (let i = 0; i < 4; i++) {
            array[i] = this.uuid.getInt32(i * 4, false).toString();
        }
        return `[I;${array.map(e => e.trim()).join(',')}]`
    }
    toHex() {
        const hex = (
            this.uuid.getBigUint64(0, false).toString(16).padStart(16, '0') +
            this.uuid.getBigUint64(8, false).toString(16).padStart(16, '0'));
        const groups = [];
        let groupStart = 0;
        for (const groupSize of UUID_GROUP_SIZES) {
            groups.push(hex.substring(groupStart, groupStart + groupSize));
            groupStart += groupSize;
        }
        return groups.join('-');
    }
    toMostLeast() {
        const most = this.uuid.getBigInt64(0, false).toString();
        const least = this.uuid.getBigInt64(8, false).toString();
        return `Most:${most.trim()}L,Least:${least.trim()}L`
    }
}



